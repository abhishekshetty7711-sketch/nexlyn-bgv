package com.nexlyn.bgv.reports.internal.model;

import java.util.List;

/**
 * The report as the templates see it: one record per kind of page, every value already formatted
 * (dates, phone numbers, masked numbers) and every image already embedded as a data URI. Nothing in
 * here needs to be looked up again while rendering.
 */
public final class ReportPages {

    private ReportPages() {
    }

    /** A status badge: the CSS class of the reference tool, the label, and a key for its little icon. */
    public record Status(String cssClass, String label, String iconKey) {
    }

    /** A picture or placeholder inside a document frame. {@code imageSrc} is a data URI or null. */
    public record Frame(String header, String imageSrc, String note) {
    }

    public record Row(String label, String value, boolean tick) {
    }

    public record DetailRow(String label, String value) {
    }

    public record FreeBlock(boolean image, String text, String imageSrc) {
    }

    public record Attestation(String sealSrc, String barCouncil, String disclaimer) {
    }

    /** One card of the summary grid (page 1 or an overflow page). */
    public record SummaryCard(String iconKey, String title, String description, Status status) {
    }

    /** The heading bar of a detail page. {@code continued} adds the "— Continued" mark. */
    public record TitleBar(String iconKey, String title, String docName, Status status, boolean continued) {
    }

    /** The coloured pill under the header on page 1. */
    public record Pill(String background, String border, String textColor, String iconBackground, String iconShapes,
                       String title, String subtitle) {
    }

    /** Remarks blocks. The texts are bold-only HTML, safe to print as they are. */
    public record Remarks(String analystHtml, String finalHtml) {
    }

    /** What every page knows about its place in the document. */
    public sealed interface Page permits Cover, RemarksPage, OverflowPage, DetailPage, DocumentPage, ServicesPage {
        int number();

        int total();

        /** A short name for the template to switch on. */
        String kind();
    }

    public record Cover(int number, int total, String reportId, String issueDate, String companyName, Pill pill,
                        String fullName, String parentLabel, String parentName, String employeeId, String dob,
                        String phone, String photoSrc, boolean showPeriod, String periodStart, String periodEnd,
                        String overviewTotal, String overviewCompleted, String overviewStatus,
                        List<SummaryCard> summary, boolean compact, Remarks inlineRemarks) implements Page {
        @Override
        public String kind() {
            return "cover";
        }
    }

    public record RemarksPage(int number, int total, Remarks remarks) implements Page {
        @Override
        public String kind() {
            return "remarks";
        }
    }

    public record OverflowPage(int number, int total, List<SummaryCard> cards, boolean compact, Remarks remarks) implements Page {
        @Override
        public String kind() {
            return "overflow";
        }
    }

    /** The main page of one check. {@code frames} are the documents that stay on this page. */
    public record DetailPage(int number, int total, TitleBar title, List<Row> rows, List<DetailRow> details,
                             String remarksHtml, List<FreeBlock> freeBlocks, Attestation attestation,
                             List<Frame> frames) implements Page {
        @Override
        public String kind() {
            return "detail";
        }
    }

    /** A document moved to its own page. {@code large} is the "larger box". */
    public record DocumentPage(int number, int total, TitleBar title, Frame frame, boolean large) implements Page {
        @Override
        public String kind() {
            return "document";
        }
    }

    public record ServicesPage(int number, int total) implements Page {
        @Override
        public String kind() {
            return "services";
        }
    }

    /** The whole report. */
    public record ReportDocument(String reportId, boolean watermark, String watermarkText, int watermarkSize, List<Page> pages) {
        public int total() {
            return pages.size();
        }
    }
}
