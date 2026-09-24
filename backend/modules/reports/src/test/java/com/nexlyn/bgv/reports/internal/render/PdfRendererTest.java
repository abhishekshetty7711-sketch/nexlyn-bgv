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
}
