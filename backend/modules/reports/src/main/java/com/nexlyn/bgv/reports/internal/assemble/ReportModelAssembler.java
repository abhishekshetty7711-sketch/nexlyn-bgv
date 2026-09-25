package com.nexlyn.bgv.reports.internal.assemble;

import com.nexlyn.bgv.cases.CaseReport;
import com.nexlyn.bgv.cases.CaseReport.Check;
import com.nexlyn.bgv.common.enums.CheckStatus;
import com.nexlyn.bgv.common.validation.BoldOnlyHtml;
import com.nexlyn.bgv.documents.DocumentApi;
import com.nexlyn.bgv.documents.DocumentApi.DocumentInfo;
import com.nexlyn.bgv.reports.internal.layout.Pagination;
import com.nexlyn.bgv.reports.internal.model.ReportPages.Attestation;
import com.nexlyn.bgv.reports.internal.model.ReportPages.Cover;
import com.nexlyn.bgv.reports.internal.model.ReportPages.DetailPage;
import com.nexlyn.bgv.reports.internal.model.ReportPages.DetailRow;
import com.nexlyn.bgv.reports.internal.model.ReportPages.DocumentPage;
import com.nexlyn.bgv.reports.internal.model.ReportPages.Frame;
import com.nexlyn.bgv.reports.internal.model.ReportPages.FreeBlock;
import com.nexlyn.bgv.reports.internal.model.ReportPages.OverflowPage;
import com.nexlyn.bgv.reports.internal.model.ReportPages.Page;
import com.nexlyn.bgv.reports.internal.model.ReportPages.Remarks;
import com.nexlyn.bgv.reports.internal.model.ReportPages.RemarksPage;
import com.nexlyn.bgv.reports.internal.model.ReportPages.ReportDocument;
import com.nexlyn.bgv.reports.internal.model.ReportPages.Row;
import com.nexlyn.bgv.reports.internal.model.ReportPages.ServicesPage;
import com.nexlyn.bgv.reports.internal.model.ReportPages.SummaryCard;
import com.nexlyn.bgv.reports.internal.model.ReportPages.TitleBar;
import com.nexlyn.bgv.reports.internal.render.ImageEmbedder;
import com.nexlyn.bgv.reports.internal.render.ReportFormat;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Builds the pages of a report from the case and its documents (CLAUDE.md section 13, steps 1-2).
 * The page plan comes from {@link Pagination}; this class fills each page with formatted values and
 * embedded pictures. It reads only: nothing here changes a case.
 */
@Component
public class ReportModelAssembler {

    /** The advocate's Bar Council number and the wording used when a check leaves them blank (CLAUDE.md section 6.2). */
    public static final String DEFAULT_BAR_COUNCIL = "KAR/670/06";
    public static final String DEFAULT_DISCLAIMER = "This report is based on information available in accessible court records and databases "
            + "at the time of verification. While due care has been taken, the completeness and accuracy of records cannot be guaranteed "
            + "due to limitations in record availability and updates. This report is issued solely for background verification purposes.";

    private final DocumentApi documents;
    private final ImageEmbedder images;
    private final String sealSrc;

    public ReportModelAssembler(DocumentApi documents, ImageEmbedder images, AssetSource assets) {
        this.documents = documents;
        this.images = images;
        this.sealSrc = assets.advocateSealDataUri();
    }

    /**
     * The report and, for each detail page, which supporting documents sit on it (not on pages of their own).
     * The job runner uses it to move a document to its own page when the browser finds that it does not fit.
     */
    public record Assembly(ReportDocument document, Map<Integer, List<UUID>> flowDocumentsByPage) {

        /** The documents that stay on this page, in order (empty when the page has none or is not a detail page). */
        public List<UUID> flowDocuments(int pageNumber) {
            return flowDocumentsByPage.getOrDefault(pageNumber, List.of());
        }
    }

    /** The whole report, page by page. */
    public ReportDocument assemble(CaseReport report) {
        return assemble(report, Set.of()).document();
    }

    /**
     * The whole report, with the documents in {@code forcedToNextPage} placed on pages of their own as if the analyst
     * had chosen "Move to Next Page" for them (the reference tool leaves that to the analyst, who must notice a
     * cut-off page; see the job runner).
     */
    public Assembly assemble(CaseReport report, Set<UUID> forcedToNextPage) {
        String dateFormat = report.settings().dateFormat();
        List<Check> checks = report.checks();

        Map<UUID, List<DocumentInfo>> docsByCheck = new HashMap<>();
        for (Check check : checks) {
            docsByCheck.put(check.id(), documents.supportingDocuments(check.id()));
        }
        List<String> groups = checks.stream().map(Check::iconGroup).toList();
        List<Integer> moved = checks.stream()
                .map(c -> (int) docsByCheck.get(c.id()).stream().filter(d -> movesToNextPage(d, forcedToNextPage)).count())
                .toList();
        Pagination.Plan plan = Pagination.plan(groups, moved, report.settings().layoutCards());
        boolean compact = plan.layout() == 6;
        int total = plan.totalPages();

        Remarks remarks = new Remarks(remarkHtml(report.analystRemarks()), remarkHtml(report.finalRecommendation()));
        List<SummaryCard> allCards = plan.groups().stream().map(group -> summaryCard(group, checks)).toList();

        List<Page> pages = new ArrayList<>();
        pages.add(cover(report, plan, allCards, remarks, compact, total));
        if (plan.dedicatedRemarks()) {
            pages.add(new RemarksPage(pages.size() + 1, total, remarks));
        }
        for (Pagination.OverflowPage overflow : plan.overflow()) {
            List<SummaryCard> cards = overflow.groupIndexes().stream().map(allCards::get).toList();
            pages.add(new OverflowPage(pages.size() + 1, total, cards, compact, overflow.withRemarks() ? remarks : null));
        }
        Map<Integer, List<UUID>> flowDocuments = new HashMap<>();
        for (Check check : checks) {
            addCheckPages(pages, total, check, docsByCheck.get(check.id()), dateFormat, forcedToNextPage, flowDocuments);
        }
        pages.add(new ServicesPage(pages.size() + 1, total));
        return new Assembly(new ReportDocument(report.reportId(), report.settings().watermarkEnabled(),
                watermarkText(report.settings().watermarkText()), watermarkSize(watermarkText(report.settings().watermarkText())), List.copyOf(pages)),
                flowDocuments);
    }

    private static boolean movesToNextPage(DocumentInfo doc, Set<UUID> forced) {
        // a larger box only exists on a page of its own (the API stores the two together; this also covers older rows)
        return doc.moveToNextPage() || doc.useLargerBox() || forced.contains(doc.id());
    }

    // ---- page 1 -------------------------------------------------------------------------------------------

    private Cover cover(CaseReport report, Pagination.Plan plan, List<SummaryCard> allCards, Remarks remarks, boolean compact, int total) {
        String format = report.settings().dateFormat();
        CaseReport.Candidate k = report.candidate();
        List<SummaryCard> cards = plan.coverGroups().stream().map(allCards::get).toList();
        return new Cover(1, total, ReportFormat.orDash(report.reportId()), ReportFormat.dateOrDash(report.issueDate(), format),
                ReportFormat.orDash(report.companyName()), ReportFormat.pill(report.pill().preset(), report.pill().title(), report.pill().subtitle()),
                ReportFormat.orDash(k.fullName()), k.guardian() ? "Guardian Name" : "Father Name", ReportFormat.orDash(k.parentName()),
                ReportFormat.orDash(k.employeeId()), ReportFormat.dateOrDash(k.dob(), format), ReportFormat.orDash(k.phoneDisplay()),
                photo(k.photoDocumentId()), report.period().show(), ReportFormat.dateOrDash(report.period().start(), format),
                ReportFormat.dateOrDash(report.period().end(), format), ReportFormat.twoDigits(report.overview().total()),
                ReportFormat.twoDigits(report.overview().completed()), ReportFormat.orDash(report.overview().overallStatus()),
                cards, compact, plan.remarksInline() ? remarks : null);
    }

    /** The candidate photo (cropped when a crop is saved), or null when there is none or it cannot be read. */
    private String photo(UUID documentId) {
        if (documentId == null) {
            return null;
        }
        return embedded(documentId);
    }

    /** One summary card for a group of checks: the first check names it, the most serious status is shown. */
    private static SummaryCard summaryCard(Pagination.Group group, List<Check> checks) {
        Check first = checks.get(group.checkIndexes().get(0));
        CheckStatus worst = group.checkIndexes().stream()
                .map(i -> checks.get(i).status())
                .max(Comparator.comparingInt(ReportFormat::severity))
                .orElse(first.status());
        return new SummaryCard(first.iconGroup(), first.title(), first.summaryDescription(), ReportFormat.status(worst));
    }

    // ---- a check --------------------------------------------------------------------------------------------

    private void addCheckPages(List<Page> pages, int total, Check check, List<DocumentInfo> docs, String dateFormat,
                               Set<UUID> forced, Map<Integer, List<UUID>> flowDocuments) {
        String docName = check.cardVerifies();
        var status = ReportFormat.status(check.status());
        TitleBar main = new TitleBar(check.iconGroup(), check.title(), docName, status, false);
        TitleBar continuedBar = new TitleBar(check.iconGroup(), check.title(), docName, status, true);

        // Documents on the detail page are numbered 1, 2, 3; moved ones continue the count, in page order.
        List<DocumentInfo> staying = docs.stream().filter(d -> !movesToNextPage(d, forced)).toList();
        List<DocumentInfo> movedDocs = docs.stream().filter(d -> movesToNextPage(d, forced)).toList();
        flowDocuments.put(pages.size() + 1, staying.stream().map(DocumentInfo::id).toList());
        List<Frame> frames = new ArrayList<>();
        for (int i = 0; i < staying.size(); i++) {
            frames.add(frame(staying.get(i), i + 1, docName));
        }

        List<Row> rows = check.fields().stream()
                .map(f -> new Row(f.label(), ReportFormat.fieldValue(f.type(), f.value(), dateFormat), f.verifiedTick()))
                .toList();

        pages.add(new DetailPage(pages.size() + 1, total, main, rows, detailRows(check, dateFormat),
                blank(check.remarks()) ? null : BoldOnlyHtml.sanitize(check.remarks()), freeBlocks(check),
                check.hasAttestation() ? attestation(check) : null, frames));

        for (int i = 0; i < movedDocs.size(); i++) {
            DocumentInfo doc = movedDocs.get(i);
            pages.add(new DocumentPage(pages.size() + 1, total, continuedBar, frame(doc, staying.size() + i + 1, docName),
                    // a document moved here by the job runner has the page to itself, so it gets the big box too
                    doc.useLargerBox() || (forced.contains(doc.id()) && !doc.moveToNextPage())));
        }
    }

    /** Verification Type, Document Type, the two dates, then the check's own extra rows. */
    private static List<DetailRow> detailRows(Check check, String dateFormat) {
        List<DetailRow> rows = new ArrayList<>();
        rows.add(new DetailRow("Verification Type", check.verificationType()));
        rows.add(new DetailRow("Document Type", check.documentName()));
        rows.add(new DetailRow("Requested Date", ReportFormat.date(check.requestedDate(), dateFormat)));
        rows.add(new DetailRow("Completed Date", ReportFormat.date(check.completedDate(), dateFormat)));
        for (CaseReport.Detail extra : check.details()) {
            String value = extra.value() == null ? "" : extra.value();
            rows.add(new DetailRow(extra.label(), extra.label().contains("Date") ? formatIfDate(value, dateFormat) : value));
        }
        return rows;
    }

    private static String formatIfDate(String value, String dateFormat) {
        return ReportFormat.fieldValue("date", value, dateFormat);
    }

    private List<FreeBlock> freeBlocks(Check check) {
        List<FreeBlock> blocks = new ArrayList<>();
        for (CaseReport.FreeBlock block : check.freeBlocks()) {
            if ("IMAGE".equals(block.kind())) {
                String src = block.documentId() == null ? null : embedded(block.documentId());
                blocks.add(new FreeBlock(true, null, src));
            } else if (!blank(block.text())) {
                blocks.add(new FreeBlock(false, block.text(), null));
            }
        }
        return blocks;
    }

    private Attestation attestation(Check check) {
        return new Attestation(sealSrc, blank(check.barCouncilNo()) ? DEFAULT_BAR_COUNCIL : check.barCouncilNo(),
                blank(check.disclaimer()) ? DEFAULT_DISCLAIMER : check.disclaimer());
    }

    // ---- documents ---------------------------------------------------------------------------------------------

    /** "Document N — what it verifies — its label", with the picture (a PDF shows its first page). */
    private Frame frame(DocumentInfo doc, int number, String docName) {
        String header = "Document " + number + " — " + docName + " — " + doc.displayLabel();
        byte[] bytes;
        try {
            bytes = documents.content(doc.id());
        } catch (RuntimeException e) {
            return new Frame(header, null, "This document could not be loaded.");
        }
        if (doc.isPdf()) {
            return images.pdfFirstPage(bytes)
                    .map(preview -> new Frame(header, preview.dataUri(),
                            "PDF document" + (preview.pages() > 1 ? " — page 1 of " + preview.pages() : "")))
                    .orElseGet(() -> new Frame(header, null, "This PDF could not be shown."));
        }
        return images.picture(bytes, doc.mimeType(), doc.crop())
                .map(src -> new Frame(header, src, null))
                .orElseGet(() -> new Frame(header, null, "This picture could not be shown."));
    }

    /** A stored picture as a data URI, or null when it is not a picture, is gone, or cannot be read: one bad file never fails a report. */
    private String embedded(UUID documentId) {
        try {
            return documents.find(documentId)
                    .filter(DocumentInfo::isImage)
                    .flatMap(info -> images.picture(documents.content(documentId), info.mimeType(), info.crop()))
                    .orElse(null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---- small things --------------------------------------------------------------------------------------------

    private static String remarkHtml(String stored) {
        return blank(stored) ? ReportFormat.DASH : BoldOnlyHtml.sanitize(stored);
    }

    private static String watermarkText(String text) {
        return blank(text) ? "NEXLYN VERIFIED" : text.trim();
    }

    /**
     * The watermark's font size in pixels: 92 like the reference tool for short texts, smaller for long ones so
     * the whole text stays inside the page width instead of being cut off at both ends.
     */
    static int watermarkSize(String text) {
        int size = (int) Math.round(760.0 / (Math.max(1, text.length()) * 0.7));
        return Math.max(24, Math.min(92, size));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
