package com.nexlyn.bgv.reports.internal.service;

import com.nexlyn.bgv.auth.AdminDirectory;
import com.nexlyn.bgv.auth.AuditEvent;
import com.nexlyn.bgv.cases.CaseApi;
import com.nexlyn.bgv.cases.CaseReport;
import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.documents.FileStorage;
import com.nexlyn.bgv.reports.internal.assemble.ReportModelAssembler;
import com.nexlyn.bgv.reports.internal.domain.ReportJob;
import com.nexlyn.bgv.reports.internal.domain.ReportVersion;
import com.nexlyn.bgv.reports.internal.render.HtmlRenderer;
import com.nexlyn.bgv.reports.internal.render.PdfRenderer;
import com.nexlyn.bgv.reports.internal.repository.ReportJobRepository;
import com.nexlyn.bgv.reports.internal.repository.ReportVersionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The work of one report job, run on a worker thread: read the case, build the pages, print the PDF,
 * store it, record the new version. Whatever happens, the job ends as DONE or FAILED (never stuck), and a
 * failure is recorded in words a person can act on; the technical detail goes to the log only.
 */
@Component
public class ReportJobRunner {

    private static final Logger log = LoggerFactory.getLogger(ReportJobRunner.class);
    private static final String GENERIC_FAILURE = "The report could not be generated. Please try again; if it keeps failing, contact support.";

    private final CaseApi cases;
    private final ReportModelAssembler assembler;
    private final HtmlRenderer html;
    private final PdfRenderer pdf;
    private final FileStorage storage;
    private final ReportJobRepository jobs;
    private final ReportVersionRepository versions;
    private final AdminDirectory directory;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;

    public ReportJobRunner(CaseApi cases, ReportModelAssembler assembler, HtmlRenderer html, PdfRenderer pdf,
                           FileStorage storage, ReportJobRepository jobs, ReportVersionRepository versions,
                           AdminDirectory directory, ApplicationEventPublisher events, TransactionTemplate tx, Clock clock) {
        this.cases = cases;
        this.assembler = assembler;
        this.html = html;
        this.pdf = pdf;
        this.storage = storage;
        this.jobs = jobs;
        this.versions = versions;
        this.directory = directory;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    /** Runs the job to its end. Never throws. */
    public void run(UUID jobId) {
        UUID caseId = null;
        UUID requestedBy = null;
        try {
            ReportJob started = tx.execute(status -> {
                ReportJob job = jobs.findById(jobId).orElseThrow();
                job.start(Instant.now(clock));
                return jobs.save(job);
            });
            caseId = started.getCaseId();
            requestedBy = started.getRequestedBy();

            CaseReport snapshot = cases.reportOf(caseId);
            PdfRenderer.Rendered rendered = renderFitting(snapshot);
            List<String> warnings = rendered.overflows().stream()
                    .map(o -> "Page " + o.page() + ": the content does not fit on the page and is cut off. "
                            + "Move a document to its own page or shorten the text, then generate again.")
                    .toList();

            int version = storeVersion(caseId, requestedBy, snapshot, rendered, warnings);
            UUID finalCaseId = caseId;
            UUID finalRequestedBy = requestedBy;
            tx.executeWithoutResult(status -> {
                ReportJob job = jobs.findById(jobId).orElseThrow();
                job.succeed(version, warnings, Instant.now(clock));
                jobs.save(job);
                audit("REPORT_GENERATED", finalRequestedBy, finalCaseId, Map.of("version", version, "pages", rendered.pageCount(),
                        "warnings", warnings.size()));
            });
        } catch (Throwable problem) { // a worker thread must never die silently; the job records the failure
            String reason = problem instanceof ApiException api ? api.getMessage() : GENERIC_FAILURE;
            log.error("Report job {} failed: {}", jobId, problem.toString(), problem);
            UUID failedCase = caseId;
            UUID failedBy = requestedBy;
            try {
                tx.executeWithoutResult(status -> {
                    jobs.findById(jobId).ifPresent(job -> {
                        job.fail(reason, Instant.now(clock));
                        jobs.save(job);
                    });
                    if (failedCase != null) {
                        audit("REPORT_GENERATION_FAILED", failedBy, failedCase, Map.of());
                    }
                });
            } catch (RuntimeException secondary) {
                log.error("Could not record the failure of report job {}: {}", jobId, secondary.toString());
            }
        }
    }

    /** The most times a page that does not fit is fixed by moving a document, and so the most extra prints a report can cost. */
    static final int MAX_LAYOUT_PASSES = 4;

    /**
     * Prints the report; when the browser finds a detail page whose content does not fit (a supporting document that
     * runs into the footer), the LAST document still on that page is moved to a page of its own and the report is
     * printed again, until everything fits or nothing more can be moved. What still does not fit is reported as a
     * warning by the caller. The reference tool leaves this to the analyst ("Move to Next Page"); doing it here means a
     * court check with its attestation block, which never leaves room for a document, needs no manual step.
     */
    private PdfRenderer.Rendered renderFitting(CaseReport snapshot) {
        Set<UUID> moved = new HashSet<>();
        ReportModelAssembler.Assembly assembly = assembler.assemble(snapshot, moved);
        PdfRenderer.Rendered rendered = pdf.render(html.render(assembly.document(), false));
        for (int pass = 0; pass < MAX_LAYOUT_PASSES && !rendered.overflows().isEmpty(); pass++) {
            Set<UUID> next = documentsToMove(assembly, rendered.overflows());
            if (next.isEmpty()) {
                break; // the content itself is too long: nothing to move
            }
            moved.addAll(next);
            log.info("Report layout: moved {} document(s) to pages of their own (pass {})", next.size(), pass + 1);
            assembly = assembler.assemble(snapshot, moved);
            rendered = pdf.render(html.render(assembly.document(), false));
        }
        return rendered;
    }

    /** For every page that does not fit: the last supporting document still on it (a page with none cannot be helped this way). */
    static Set<UUID> documentsToMove(ReportModelAssembler.Assembly assembly, List<PdfRenderer.PageOverflow> overflows) {
        Set<UUID> chosen = new HashSet<>();
        for (PdfRenderer.PageOverflow overflow : overflows) {
            List<UUID> onPage = assembly.flowDocuments(overflow.page());
            if (!onPage.isEmpty()) {
                chosen.add(onPage.get(onPage.size() - 1));
            }
        }
        return chosen;
    }

    /** Stores the PDF and its row under the next version number; a clash with a concurrent report takes the next number. */
    private int storeVersion(UUID caseId, UUID generatedBy, CaseReport snapshot, PdfRenderer.Rendered rendered, List<String> warnings) {
        String hash = sha256(rendered.pdf());
        for (int attempt = 0; attempt < 5; attempt++) {
            int version = versions.latestVersion(caseId) + 1;
            String key = "reports/" + caseId + "/v" + version + ".pdf";
            storage.put(key, rendered.pdf(), "application/pdf");
            try {
                tx.executeWithoutResult(status -> versions.saveAndFlush(new ReportVersion(caseId, version, ReportVersion.Kind.DRAFT, key,
                        hash, rendered.pdf().length, rendered.pageCount(), false, generatedBy, Instant.now(clock), warnings, snapshot)));
                return version;
            } catch (DataIntegrityViolationException clash) {
                try {
                    storage.delete(key);
                } catch (RuntimeException ignored) {
                    log.warn("Could not remove an unused report file {}", key);
                }
            }
        }
        throw new ApiException(com.nexlyn.bgv.common.error.ErrorCode.CONFLICT, "Another report for this case was being made at the same time. Please try again.");
    }

    private void audit(String action, UUID actor, UUID caseId, Map<String, Object> after) {
        String email = actor == null ? null : directory.find(List.of(actor)).values().stream().findFirst()
                .map(AdminDirectory.AdminSummary::email).orElse(null);
        events.publishEvent(new AuditEvent(action, actor, email, "CASE", caseId.toString(), caseId, null, null, null, after));
    }

    static String sha256(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
