package com.nexlyn.bgv.reports.internal.service;

import com.nexlyn.bgv.auth.AdminDirectory;
import com.nexlyn.bgv.auth.AuditEvent;
import com.nexlyn.bgv.auth.AuthApi;
import com.nexlyn.bgv.auth.CaseAccessPolicy;
import com.nexlyn.bgv.auth.CaseAction;
import com.nexlyn.bgv.cases.CaseApi;
import com.nexlyn.bgv.cases.CaseReport;
import com.nexlyn.bgv.cases.CaseValidation;
import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.common.error.ErrorCode;
import com.nexlyn.bgv.documents.FileStorage;
import com.nexlyn.bgv.reports.internal.assemble.ReportModelAssembler;
import com.nexlyn.bgv.reports.internal.domain.ReportJob;
import com.nexlyn.bgv.reports.internal.domain.ReportVersion;
import com.nexlyn.bgv.reports.internal.render.HtmlRenderer;
import com.nexlyn.bgv.reports.internal.repository.ReportJobRepository;
import com.nexlyn.bgv.reports.internal.repository.ReportVersionRepository;
import com.nexlyn.bgv.reports.internal.service.ReportViews.Download;
import com.nexlyn.bgv.reports.internal.service.ReportViews.JobView;
import com.nexlyn.bgv.reports.internal.service.ReportViews.VersionView;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reports of a case (CLAUDE.md sections 9.4 and 13): a preview of the pages, a request to make a draft
 * PDF (done in the background), and the list and download of the versions made so far.
 *
 * <p>Errors from the case's checklist (section 7.1) block a draft; warnings need the caller to say they
 * have seen them. Every method checks the permission, then the case.
 */
@Service
public class ReportService {

    private static final Set<ReportJob.Status> ACTIVE = Set.of(ReportJob.Status.QUEUED, ReportJob.Status.RUNNING);

    private final CaseApi cases;
    private final ReportModelAssembler assembler;
    private final HtmlRenderer html;
    private final CaseAccessPolicy policy;
    private final AuthApi auth;
    private final AdminDirectory directory;
    private final ReportJobRepository jobs;
    private final ReportVersionRepository versions;
    private final ReportJobExecutor executor;
    private final ReportJobRunner runner;
    private final FileStorage storage;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ReportService(CaseApi cases, ReportModelAssembler assembler, HtmlRenderer html, CaseAccessPolicy policy,
                         AuthApi auth, AdminDirectory directory, ReportJobRepository jobs, ReportVersionRepository versions,
                         ReportJobExecutor executor, ReportJobRunner runner, FileStorage storage,
                         ApplicationEventPublisher events, Clock clock) {
        this.cases = cases;
        this.assembler = assembler;
        this.html = html;
        this.policy = policy;
        this.auth = auth;
        this.directory = directory;
        this.jobs = jobs;
        this.versions = versions;
        this.executor = executor;
        this.runner = runner;
        this.storage = storage;
        this.events = events;
        this.clock = clock;
    }

    /** Jobs left unfinished by a stopped server can never finish; mark them failed so nobody waits for them. */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void failInterruptedJobs() {
        jobs.failUnfinished(Instant.now(clock));
    }

    // ---- preview ----------------------------------------------------------------------------------------

    /** The report's pages as one HTML document, for looking at before printing. Works even while errors remain. */
    @PreAuthorize("hasAnyAuthority('CASE_READ_ALL', 'CASE_READ_ASSIGNED')")
    @Transactional(readOnly = true)
    public String preview(UUID caseId) {
        policy.check(caseId, CaseAction.READ);
        CaseReport report = cases.reportOf(caseId);
        return html.render(assembler.assemble(report), true);
    }

    // ---- making a draft ----------------------------------------------------------------------------------

    /**
     * Queues a draft PDF. Refused while the case has errors, or has warnings the caller has not acknowledged,
     * or already has a report being made.
     */
    @PreAuthorize("hasAuthority('REPORT_GENERATE')")
    public JobView requestDraft(UUID caseId, boolean acknowledgeWarnings) {
        policy.check(caseId, CaseAction.GENERATE_REPORT);
        CaseValidation validation = validationOf(caseId);
        if (!validation.errors().isEmpty()) {
            throw new ApiException(ErrorCode.CONFLICT, "The report cannot be generated yet: "
                    + validation.errors().stream().map(CaseValidation.Issue::message).collect(Collectors.joining(" ")));
        }
        if (!validation.warnings().isEmpty() && !acknowledgeWarnings) {
            throw new ApiException(ErrorCode.CONFLICT, "This case has " + validation.warnings().size()
                    + " warning(s). Review them and confirm to generate the report anyway.");
        }
        if (jobs.countByCaseIdAndStatusIn(caseId, ACTIVE) > 0) {
            throw new ApiException(ErrorCode.CONFLICT, "A report for this case is already being made. Wait for it to finish.");
        }
        UUID me = auth.requireCurrentAdmin().id();
        ReportJob job = jobs.saveAndFlush(new ReportJob(caseId, me, Instant.now(clock)));
        try {
            executor.submit(() -> runner.run(job.getId()));
        } catch (ApiException busy) {
            job.fail(busy.getMessage(), Instant.now(clock));
            jobs.saveAndFlush(job);
            throw busy;
        }
        return view(job);
    }

    @PreAuthorize("hasAnyAuthority('CASE_READ_ALL', 'CASE_READ_ASSIGNED')")
    @Transactional(readOnly = true)
    public JobView job(UUID caseId, UUID jobId) {
        policy.check(caseId, CaseAction.READ);
        return view(jobs.findByIdAndCaseId(jobId, caseId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Report job not found.")));
    }

    // ---- versions ------------------------------------------------------------------------------------------

    @PreAuthorize("hasAnyAuthority('CASE_READ_ALL', 'CASE_READ_ASSIGNED')")
    @Transactional(readOnly = true)
    public List<VersionView> versions(UUID caseId) {
        policy.check(caseId, CaseAction.READ);
        List<ReportVersion> all = versions.findAllByCaseIdOrderByVersionDesc(caseId);
        Map<UUID, AdminDirectory.AdminSummary> people = directory.find(all.stream().map(ReportVersion::getGeneratedBy).collect(Collectors.toSet()));
        return all.stream().map(v -> {
            AdminDirectory.AdminSummary who = people.get(v.getGeneratedBy());
            return new VersionView(v.getVersion(), v.getKind(), v.getSizeBytes(), v.getPageCount(), v.isEncrypted(), v.getGeneratedBy(),
                    who == null ? "Unknown admin" : who.fullName(), v.getGeneratedAt(), v.getFinalizedAt(), v.getWarnings() == null ? List.of() : v.getWarnings());
        }).toList();
    }

    /**
     * The PDF of one version. Drafts need only read access to the case; a final report needs
     * {@code REPORT_DOWNLOAD_FINAL}. The stored file is checked against its recorded hash before it is sent.
     */
    @PreAuthorize("hasAnyAuthority('CASE_READ_ALL', 'CASE_READ_ASSIGNED')")
    @Transactional
    public Download download(UUID caseId, int version) {
        policy.check(caseId, CaseAction.READ);
        ReportVersion stored = versions.findByCaseIdAndVersion(caseId, version)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "That report version does not exist."));
        boolean finalReport = stored.getKind() == ReportVersion.Kind.FINAL;
        if (finalReport && !auth.requireCurrentAdmin().hasPermission("REPORT_DOWNLOAD_FINAL")) {
            throw new org.springframework.security.access.AccessDeniedException("Downloading a final report needs its own permission.");
        }
        byte[] bytes = storage.get(stored.getPdfStorageKey());
        if (!ReportJobRunner.sha256(bytes).equals(stored.getSha256())) {
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "The stored report did not pass its integrity check, so it was not sent.");
        }
        events.publishEvent(new AuditEvent("REPORT_DOWNLOADED", null, null, "CASE", caseId.toString(), caseId, null, null, null,
                Map.of("version", version, "kind", stored.getKind().name())));
        return new Download(bytes, filename(stored), finalReport);
    }

    // ---- helpers ---------------------------------------------------------------------------------------------

    private CaseValidation validationOf(UUID caseId) {
        return cases.validationOf(caseId);
    }

    /** The report ID with anything odd replaced, plus the version: NX-2026-0142_v3.pdf. */
    private static String filename(ReportVersion stored) {
        String reportId = stored.getSnapshot().reportId();
        String safe = reportId == null ? "report" : reportId.replaceAll("[^A-Za-z0-9_-]", "_");
        return safe + "_v" + stored.getVersion() + ".pdf";
    }

    private static JobView view(ReportJob job) {
        return new JobView(job.getId(), job.getCaseId(), job.getStatus(), job.getVersion(), job.getError(),
                job.getWarnings() == null ? List.of() : job.getWarnings(), job.getRequestedAt(), job.getFinishedAt());
    }
}
