package com.nexlyn.bgv.reports.internal.render;

import com.nexlyn.bgv.cases.CaseReport;
import com.nexlyn.bgv.cases.CaseReport.Check;
import com.nexlyn.bgv.common.enums.CheckStatus;
import com.nexlyn.bgv.reports.ReportFixtures;
import com.nexlyn.bgv.reports.ReportFixtures.StubDocuments;
import com.nexlyn.bgv.reports.internal.assemble.AssetSource;
import com.nexlyn.bgv.reports.internal.assemble.ReportModelAssembler;
import com.nexlyn.bgv.reports.internal.config.ReportsProperties;
import com.nexlyn.bgv.reports.internal.model.ReportPages.ReportDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The real print path: HTML into a headless Chromium into a PDF. Needs a Chrome, Chromium or Edge on the
 * machine (or CHROMIUM_PATH); without one these tests are skipped, and CI installs Chromium first.
 * The pages of the PDF built from the sample case are also saved as pictures in
 * {@code target/report-samples} so a person can look at the result.
 */
class PdfRendererTest {

    static PdfRenderer pdf;

    StubDocuments documents;
    ReportModelAssembler assembler;
    HtmlRenderer html;

    @BeforeAll
    static void browser() {
        pdf = new PdfRenderer(new ReportsProperties(null, null, null, null, null));
        assumeTrue(pdf.configuredBrowser().isPresent(), "no Chrome / Chromium / Edge found: set CHROMIUM_PATH to run the PDF tests");
    }

    @BeforeEach
    void setUp() {
        documents = new StubDocuments();
        AssetSource assets = new AssetSource();
        assembler = new ReportModelAssembler(documents, new ImageEmbedder(2400), assets);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        engine.setTemplateResolver(resolver);
        html = new HtmlRenderer(engine, assets);
    }

    private PdfRenderer.Rendered print(CaseReport report) {
        ReportDocument document = assembler.assemble(report);
        PdfRenderer.Rendered rendered = pdf.render(html.render(document, false));
        assertThat(rendered.pageCount()).as("the browser and the page plan agree").isEqualTo(document.total());
        return rendered;
    }

    /** Page text, lower case with single spaces (the print styles set some labels in capitals and break lines). */
    private static String text(byte[] pdfBytes, int page) throws IOException {
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            return stripper.getText(document).toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " ");
        }
    }

    private CaseReport sampleCase() {
        Check aadhaar = ReportFixtures.withFields(
                ReportFixtures.check("AADHAAR", "identity", "Identity Verification (Aadhaar)", CheckStatus.VERIFIED),
                List.of(new CaseReport.Field("Aadhaar Number", "aadhaar", "XXXX XXXX 0124", true),
                        new CaseReport.Field("Full Name", "text", "Asha Rao", true),
                        new CaseReport.Field("DOB", "date", "1994-05-17", true),
                        new CaseReport.Field("Father's Name", "text", "Ravi Rao", true),
                        new CaseReport.Field("Street Address", "text", "14, 3rd Cross, Indiranagar", true),
                        new CaseReport.Field("City / Town", "text", "Bengaluru", true),
                        new CaseReport.Field("State", "text", "Karnataka", true),
                        new CaseReport.Field("PIN Code", "pin", "560038", true),
                        new CaseReport.Field("Country", "text", "India", true)),
                List.of(), List.of());
        Check court = ReportFixtures.withAttestation(
                ReportFixtures.withRemarks(ReportFixtures.check("COURT", "court", "Court Record (Permanent Address)", CheckStatus.VERIFIED),
                        "No criminal or civil record was found against the candidate."), "", "");
        Check education = ReportFixtures.check("EDUCATION", "education", "Education Verification", CheckStatus.UNABLE_TO_VERIFY);
        documents.attach(aadhaar.id(), "image/png", ReportFixtures.png(900, 600, new Color(230, 240, 250)), false, false, null);
        documents.attach(court.id(), "image/jpeg", ReportFixtures.jpeg(800, 1100, new Color(250, 240, 230)), true, true, null);
        var photo = documents.photo(ReportFixtures.png(300, 380, new Color(190, 200, 210)));
        return ReportFixtures.report(List.of(aadhaar, court, education), photo);
    }

    /** Where the words "Page n of m" of every page sit, as a fraction of the page height from the top (0 = top, 1 = bottom). */
    private static List<Double> footerPositions(byte[] pdfBytes) throws IOException {
        List<Double> positions = new java.util.ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdfBytes)) {
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                final double[] found = {-1};
                PDFTextStripper stripper = new PDFTextStripper() {
                    @Override
                    protected void writeString(String text, List<org.apache.pdfbox.text.TextPosition> textPositions) {
                        if (text.startsWith("Page ") && found[0] < 0) {
                            found[0] = textPositions.get(0).getYDirAdj() / document.getPage(0).getMediaBox().getHeight();
                        }
                    }
                };
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                stripper.getText(document);
                positions.add(found[0]);
            }
        }
        return positions;
    }

    @Test
    void everyFooterStaysPinnedToTheBottomOfItsPageEvenWithAWatermark() throws Exception {
        // an old rule turned the footer into position:relative when a watermark was on, so it floated up under the content
        CaseReport plain = sampleCase();
        CaseReport watermarked = ReportFixtures.withSettings(sampleCase(), 4, "NUMERIC", true, "NEXLYN VERIFIED");
        // many groups: the summary overflows onto its own page, whose content is short (the footer must still be at the bottom)
        CaseReport overflowing = ReportFixtures.withSettings(
                ReportFixtures.report(ReportFixtures.manyGroups(6), null), 4, "NUMERIC", true, "NEXLYN VERIFIED");

        for (CaseReport report : List.of(plain, watermarked, overflowing)) {
            List<Double> positions = footerPositions(print(report).pdf());
            List<Double> found = positions.stream().filter(y -> y >= 0).toList(); // the services page has its own footer, without a page number
            assertThat(found).hasSizeGreaterThanOrEqualTo(positions.size() - 1)
                    .allSatisfy(y -> assertThat(y).as("footer position (0 top, 1 bottom)").isBetween(0.93, 1.0));
        }
    }

    @Test
    void printsEveryPlannedPageAsA4AndKeepsTheWordsSearchable() throws Exception {
        PdfRenderer.Rendered rendered = print(sampleCase());

        try (PDDocument document = Loader.loadPDF(rendered.pdf())) {
            assertThat(document.getNumberOfPages()).isEqualTo(rendered.pageCount());
            var size = document.getPage(0).getMediaBox();
            assertThat(size.getWidth()).isBetween(594f, 596f);
            assertThat(size.getHeight()).isBetween(841f, 843f);
            saveSamples(document);
        }
        String cover = text(rendered.pdf(), 1);
        assertThat(cover).contains("verification report", "nx-2026-0142", "asha rao", "candidate details", "report overview", "verification summary");
        assertThat(cover).contains("page 1 of " + rendered.pageCount());
        String last = text(rendered.pdf(), rendered.pageCount());
        assertThat(last).contains("suite of background verifications", "identity verification", "submit request");
        assertThat(rendered.overflows()).as("the sample case fits on its pages: %s", rendered.overflows()).isEmpty();
    }

    @Test
    void theDetailPageShowsTheChecksTableAndTheDocumentFrame() throws Exception {
        PdfRenderer.Rendered rendered = print(sampleCase());
        String detail = text(rendered.pdf(), 3);
        assertThat(detail).contains("identity verification (aadhaar)", "checks", "data provided", "xxxx xxxx 0124", "17/05/1994", "details",
                "verification type", "supporting documents", "document 1");
    }

    @Test
    void theAttestationAndTheLargerBoxDocumentPageArePrinted() throws Exception {
        PdfRenderer.Rendered rendered = print(sampleCase());
        StringBuilder all = new StringBuilder();
        for (int page = 1; page <= rendered.pageCount(); page++) {
            all.append(text(rendered.pdf(), page)).append('\n');
        }
        assertThat(all.toString()).contains("bar council number:", "kar/670/06", "continued", "document 1");
    }

    // ---- Move to Next Page: the printed page breaks and box sizes (CLAUDE.md 6.2, reference tool) -------------------------

    /** One check with the given documents (moveToNextPage, larger) in upload order, each a 900 x 600 picture. */
    private CaseReport oneCheckWith(boolean[]... options) {
        Check id = ReportFixtures.check("AADHAAR", "identity", "Identity Verification (Aadhaar)", CheckStatus.VERIFIED);
        for (boolean[] option : options) {
            documents.attach(id.id(), "image/png", ReportFixtures.png(900, 600, new Color(230, 240, 250)), option[0], option[1], null);
        }
        return ReportFixtures.report(List.of(id), null);
    }

    private static List<PdfRenderer.FrameBox> onPage(PdfRenderer.Rendered rendered, int page) {
        return rendered.frames().stream().filter(f -> f.page() == page).toList();
    }

    @Test
    void aMovedDocumentStartsANewPageInTheStandardBox() throws Exception {
        PdfRenderer.Rendered rendered = print(oneCheckWith(new boolean[]{false, false}, new boolean[]{true, false}));

        // cover, detail page, the moved document's page, services
        assertThat(rendered.pageCount()).isEqualTo(4);
        assertThat(rendered.overflows()).isEmpty();
        assertThat(onPage(rendered, 2)).as("the document that stays is on the detail page").hasSize(1);
        assertThat(onPage(rendered, 3)).as("the moved document has a page of its own").hasSize(1);
        assertThat(onPage(rendered, 3).get(0).large()).isFalse();
        // the standard box of a page of its own is 400 px tall, like the reference tool
        assertThat(onPage(rendered, 3).get(0).heightPx()).isBetween(398, 402);
        assertThat(onPage(rendered, 3).get(0).heightPx()).isLessThan(500);

        String detail = text(rendered.pdf(), 2);
        String moved = text(rendered.pdf(), 3);
        assertThat(detail).contains("document 1").doesNotContain("document 2");
        assertThat(moved).contains("continued", "supporting documents", "document 2", "additional document 1");
        assertThat(moved).as("no details table on the document's page").doesNotContain("data provided");
    }

    @Test
    void theFirstDocumentMovedMeansTheOtherOneIsNumberedFirstAndTheMovedOneContinuesTheCount() throws Exception {
        PdfRenderer.Rendered rendered = print(oneCheckWith(new boolean[]{true, false}, new boolean[]{false, false}));
        assertThat(rendered.pageCount()).isEqualTo(4);
        // numbered in page order: the one that stays is "Document 1" (labelled by its place among the uploads), the moved one "Document 2"
        assertThat(text(rendered.pdf(), 2)).contains("document 1").contains("additional document 1").doesNotContain("document 2");
        assertThat(text(rendered.pdf(), 3)).contains("document 2").contains("original document");
    }

    @Test
    void everyMovedDocumentGetsItsOwnPageAfterTheDetailPage() {
        PdfRenderer.Rendered rendered = print(oneCheckWith(new boolean[]{true, false}, new boolean[]{true, false}, new boolean[]{true, false}));
        assertThat(rendered.pageCount()).isEqualTo(1 + 1 + 3 + 1);
        assertThat(onPage(rendered, 2)).as("nothing stays on the detail page").isEmpty();
        for (int page = 3; page <= 5; page++) {
            assertThat(onPage(rendered, page)).hasSize(1);
        }
        assertThat(rendered.overflows()).isEmpty();
    }

    @Test
    void aDocumentThatIsNotMovedStaysOnTheDetailPageInTheSmallBox() {
        PdfRenderer.Rendered rendered = print(oneCheckWith(new boolean[]{false, false}));
        assertThat(rendered.pageCount()).isEqualTo(3);
        assertThat(onPage(rendered, 2)).hasSize(1);
        assertThat(onPage(rendered, 2).get(0).heightPx()).as("the box on the detail page has the reference's 200-380 px range").isBetween(200, 380);
    }

    @Test
    void tooMuchOnOnePageIsReportedWithItsPageNumber() {
        Check id = ReportFixtures.check("AADHAAR", "identity", "Identity", CheckStatus.VERIFIED);
        for (int i = 0; i < 4; i++) {
            documents.attach(id.id(), "image/png", ReportFixtures.png(900, 900, Color.WHITE), false, false, null);
        }
        PdfRenderer.Rendered rendered = print(ReportFixtures.report(List.of(id), null));
        assertThat(rendered.overflows()).hasSize(1);
        assertThat(rendered.overflows().get(0).page()).as("the detail page").isEqualTo(2);
        assertThat(rendered.overflows().get(0).overflowPixels()).isGreaterThan(10);
    }

    @Test
    void aReportWithManyGroupsStillMatchesThePlan() {
        PdfRenderer.Rendered rendered = print(ReportFixtures.report(ReportFixtures.manyGroups(7), null));
        assertThat(rendered.pageCount()).isEqualTo(1 + 1 + 7 + 1);
        assertThat(rendered.overflows()).isEmpty();
    }

    @Test
    void theWatermarkIsPrintedOnEveryPageWhenSwitchedOn() throws Exception {
        Check id = ReportFixtures.check("AADHAAR", "identity", "Identity", CheckStatus.VERIFIED);
        CaseReport report = ReportFixtures.withSettings(ReportFixtures.report(List.of(id), null), 4, "NUMERIC", true, "CONFIDENTIAL COPY");
        PdfRenderer.Rendered rendered = print(report);
        for (int page = 1; page <= rendered.pageCount(); page++) {
            assertThat(text(rendered.pdf(), page)).contains("confidential copy");
        }
    }

    private static void saveSamples(PDDocument document) throws IOException {
        Path folder = Path.of("target", "report-samples");
        Files.createDirectories(folder);
        PDFRenderer renderer = new PDFRenderer(document);
        for (int i = 0; i < document.getNumberOfPages(); i++) {
            ImageIO.write(renderer.renderImageWithDPI(i, 70), "png", folder.resolve("page-" + (i + 1) + ".png").toFile());
        }
    }

    // ---- fonts: the report must be printed in its own bundled fonts, on every machine -----------------------------------
    // The brand block and footer of the reference tool name no font file, only the system stack ('Segoe UI', Arial ...), so
    // the container printed them in Liberation Sans while a Windows browser showed Segoe UI. Selawik is bundled now.
    // These tests read the fonts out of the finished PDF, so they fail wherever the bundled fonts are not the ones used.

    @Test
    void everyFontInAReportIsBundledAndEmbedded() throws Exception {
        byte[] pdfBytes = print(sampleCase()).pdf();

        var fonts = com.nexlyn.bgv.reports.internal.render.ReportFontAudit.inspect(pdfBytes, com.nexlyn.bgv.reports.internal.render.ReportFontAudit.BUNDLED);
        assertThat(fonts.problems()).as("fonts in the PDF: %s", fonts.families()).isEmpty();
        assertThat(fonts.families()).containsExactlyInAnyOrder("Inter", "Selawik");

        // the words of the brand block and the title on page 1, and the footer of page 2, are drawn in Selawik
        var cover = com.nexlyn.bgv.reports.internal.render.ReportFontAudit.familiesOfWords(pdfBytes, 1,
                List.of("NEXLYN", "SERVICES", "VERIFY", "VALIDATE", "TRUST", "Verification", "Page"));
        assertThat(cover).hasSize(7).allSatisfy((word, family) -> assertThat(family).as(word).isEqualTo("Selawik"));
        var inner = com.nexlyn.bgv.reports.internal.render.ReportFontAudit.familiesOfWords(pdfBytes, 2, List.of("Page", "Verified"));
        assertThat(inner).containsEntry("Page", "Selawik");
        // while the report's own text stays Inter
        assertThat(com.nexlyn.bgv.reports.internal.render.ReportFontAudit.familiesOfWords(pdfBytes, 1, List.of("Asha")))
                .containsEntry("Asha", "Inter");
    }

    @Test
    void theFontCheckPageIsPrintedInTheBundledFonts() {
        byte[] sample = pdf.render(html.fontCheckPage()).pdf();
        assertThat(com.nexlyn.bgv.reports.internal.render.ReportFontAudit.checkSamplePage(sample)).isEmpty();
    }

    @Test
    void aMissingFontIsCaughtInsteadOfPrintedInSomethingElse() {
        // what a broken container looks like: the brand and footer ask for a font that is not there
        String broken = html.fontCheckPage().replace("font-family: 'Selawik', sans-serif;", "font-family: 'NoSuchBrandFont', serif;");
        assertThat(broken).isNotEqualTo(html.fontCheckPage());
        var problems = com.nexlyn.bgv.reports.internal.render.ReportFontAudit.checkSamplePage(pdf.render(broken).pdf());
        assertThat(problems).isNotEmpty().anySatisfy(p -> assertThat(p).containsAnyOf("not one of the bundled", "is drawn in"));
    }

    @Test
    void theStartUpSelfCheckPassesHereAndStopsAnApplicationWhoseFontsAreBroken() {
        var good = new com.nexlyn.bgv.reports.internal.render.ReportFontSelfCheck(html, pdf, "fail");
        assertThatCode(() -> good.run(null)).doesNotThrowAnyException();

        HtmlRenderer brokenHtml = org.mockito.Mockito.spy(html);
        org.mockito.Mockito.doReturn(html.fontCheckPage().replace("font-family: 'Selawik', sans-serif;", "font-family: 'NoSuchBrandFont', serif;"))
                .when(brokenHtml).fontCheckPage();
        assertThatThrownBy(() -> new com.nexlyn.bgv.reports.internal.render.ReportFontSelfCheck(brokenHtml, pdf, "fail").run(null))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("font check FAILED");
        assertThatCode(() -> new com.nexlyn.bgv.reports.internal.render.ReportFontSelfCheck(brokenHtml, pdf, "warn").run(null))
                .as("warn mode only logs").doesNotThrowAnyException();
        assertThatCode(() -> new com.nexlyn.bgv.reports.internal.render.ReportFontSelfCheck(brokenHtml, pdf, "off").run(null))
                .as("off mode does nothing").doesNotThrowAnyException();
    }
}
