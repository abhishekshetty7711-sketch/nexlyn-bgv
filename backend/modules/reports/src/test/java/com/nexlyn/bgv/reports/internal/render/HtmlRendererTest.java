package com.nexlyn.bgv.reports.internal.render;

import com.nexlyn.bgv.cases.CaseReport;
import com.nexlyn.bgv.cases.CaseReport.Check;
import com.nexlyn.bgv.common.enums.CheckStatus;
import com.nexlyn.bgv.reports.ReportFixtures;
import com.nexlyn.bgv.reports.ReportFixtures.StubDocuments;
import com.nexlyn.bgv.reports.internal.assemble.AssetSource;
import com.nexlyn.bgv.reports.internal.assemble.ReportModelAssembler;
import com.nexlyn.bgv.reports.internal.model.ReportPages.ReportDocument;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The HTML of a report: structure, escaping, and that nothing is left unprocessed. */
class HtmlRendererTest {

    StubDocuments documents;
    ReportModelAssembler assembler;
    HtmlRenderer renderer;

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
        renderer = new HtmlRenderer(engine, assets);
    }

    private Document render(CaseReport report, boolean preview) {
        ReportDocument doc = assembler.assemble(report);
        return Jsoup.parse(renderer.render(doc, preview));
    }

    private Document render(List<Check> checks) {
        return render(ReportFixtures.report(checks, null), false);
    }

    @Test
    void everyPlannedPageIsOnePageElementAndTheServicesPageIsLast() {
        Document html = render(ReportFixtures.manyGroups(3));
        var pages = html.select("#pagesArea > .page");
        assertThat(pages).hasSize(6);
        assertThat(pages.last().id()).isEqualTo("servicesPage");
        assertThat(html.select("#page1")).hasSize(1);
        // footers carry "Page N of 6"
        assertThat(html.select(".pf-pagenum").eachText()).containsExactly("Page 1 of 6", "Page 2 of 6", "Page 3 of 6", "Page 4 of 6", "Page 5 of 6", "Page 6 of 6");
    }

    @Test
    void theCoverCarriesTheCandidateOverviewSummaryAndInlineRemarks() {
        Check id = ReportFixtures.check("AADHAAR", "identity", "Identity Verification (Aadhaar)", CheckStatus.VERIFIED);
        Document html = render(List.of(id));
        var cover = html.selectFirst("#page1");

        assertThat(cover.select(".top-info .val").eachText()).containsExactly("NX-2026-0142", "11/06/2026", "Acme Corp Private Limited");
        assertThat(cover.selectFirst(".status-text h2").text()).isEqualTo("Completed");
        assertThat(cover.select(".detail-group .val").eachText()).contains("Asha Rao", "Ravi Rao", "EMP-1001");
        assertThat(cover.text()).contains("17/05/1994", "+91 98765 43210", "19/05/2026", "11/06/2026");
        assertThat(cover.select(".ov-stat-text .val").eachText()).containsExactly("01", "01", "Clear");
        assertThat(cover.select(".summary-card .sum-card-title").eachText()).containsExactly("Identity Verification (Aadhaar)");
        assertThat(cover.select(".summary-card .status-badge").text()).isEqualTo("Verified");
        assertThat(cover.select(".remarks-content p").html()).contains("<strong>completed</strong>");
        assertThat(cover.select(".final-rec-content p").text()).isEqualTo("Approved for the next stage.");
        assertThat(cover.select(".pf-legend-row")).as("page 1 has no status legend").isEmpty();
    }

    @Test
    void theCompanyNameKeepsItsLineBreak() {
        String raw = renderer.render(assembler.assemble(ReportFixtures.report(ReportFixtures.manyGroups(1), null)), false);
        assertThat(raw).contains("Acme Corp\nPrivate Limited");
        assertThat(raw).contains("white-space:pre-line");
    }

    @Test
    void theHiddenPeriodRowIsNotInTheHtmlAtAll() {
        Check id = ReportFixtures.check("AADHAAR", "identity", "Identity", CheckStatus.VERIFIED);
        CaseReport base = ReportFixtures.report(List.of(id), null);
        CaseReport hidden = new CaseReport(base.caseId(), base.reportId(), base.lifecycle(), base.issueDate(), base.companyName(),
                base.candidate(), new CaseReport.Period(false, base.period().start(), base.period().end()), base.pill(), base.overview(),
                base.analystRemarks(), base.finalRecommendation(), base.settings(), base.checks());
        Document html = render(hidden, false);
        assertThat(html.text()).doesNotContain("Verification Period");
        assertThat(render(List.of(id)).text()).contains("Verification Period");
    }

    @Test
    void theDetailPageHasTheChecksTableTheDetailsGridAndTheFooterLegend() {
        Check id = ReportFixtures.check("AADHAAR", "identity", "Identity Verification (Aadhaar)", CheckStatus.DISCREPANCY);
        Document html = render(List.of(id));
        var page = html.select("#pagesArea > .page").get(1);

        assertThat(page.select(".det-title-text").text()).isEqualTo("Identity Verification (Aadhaar)(Verifies Identity Verification (Aadhaar))");
        assertThat(page.select(".detail-section-title .status-badge").text()).isEqualTo("Discrepancy");
        assertThat(page.select(".detail-section-title .status-badge").hasClass("badge-discrepancy")).isTrue();
        assertThat(page.select(".checks-table thead th").eachText()).containsExactly("Checks", "Data Provided");
        assertThat(page.select(".checks-table tbody tr")).hasSize(2);
        assertThat(page.select(".checks-table tbody tr").first().select(".tick-green")).hasSize(1);
        assertThat(page.select(".checks-table tbody tr").last().select(".tick-green")).isEmpty();
        assertThat(page.select(".det-label").eachText()).containsExactly("Verification Type", "Document Type", "Requested Date", "Completed Date");
        assertThat(page.select(".pf-leg")).hasSize(4);
    }

    @Test
    void aBlankTextBlockPrintsAsEightyPixelsOfBlankSpaceAndATextBlockAsABox() {
        Check check = ReportFixtures.withFields(ReportFixtures.check("POLICE", "POLICE", "Police Verification", CheckStatus.VERIFIED), List.of(), List.of(),
                List.of(new CaseReport.FreeBlock("TEXT", "Visited the station.", null), new CaseReport.FreeBlock("TEXT", "", null)));
        var page = render(List.of(check)).select("#pagesArea > .page").get(1);

        assertThat(page.select(".free-blank")).as("one blank space").hasSize(1);
        assertThat(page.select(".free-blank").attr("style")).contains("height:80px");
        assertThat(page.text()).contains("Visited the station.");
    }

    @Test
    void everythingUserTypedIsEscapedAndOnlyBoldSurvivesInRemarks() {
        Check evil = ReportFixtures.withRemarks(ReportFixtures.check("AADHAAR", "identity", "<img src=x onerror=alert(1)>", CheckStatus.VERIFIED),
                "<script>alert(1)</script><b>bold</b> & <img src=x>");
        CaseReport report = ReportFixtures.report(List.of(evil), null);
        CaseReport hostile = new CaseReport(report.caseId(), "<b>NX</b>", report.lifecycle(), report.issueDate(), "<script>x</script>",
                report.candidate(), report.period(), report.pill(), report.overview(), "<script>steal()</script>", "<iframe src=x>", report.settings(), report.checks());
        String raw = renderer.render(assembler.assemble(hostile), false);
        Document html = Jsoup.parse(raw);

        assertThat(html.select("script")).isEmpty();
        assertThat(html.select("iframe")).isEmpty();
        assertThat(html.select("img[onerror]")).isEmpty();
        assertThat(html.select("#page1 .val").first().text()).isEqualTo("<b>NX</b>");
        assertThat(html.select("#pagesArea > .page").get(1).select("p").html()).contains("<strong>bold</strong>").doesNotContain("<img");
        assertThat(raw).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
    }

    @Test
    void theWatermarkAppearsOnEveryPageOnlyWhenSwitchedOn() {
        Check id = ReportFixtures.check("AADHAAR", "identity", "Identity", CheckStatus.VERIFIED);
        CaseReport on = ReportFixtures.withSettings(ReportFixtures.report(List.of(id), null), 4, "NUMERIC", true, "CONFIDENTIAL");
        Document html = render(on, false);
        assertThat(html.body().hasClass("with-watermark")).isTrue();
        assertThat(html.select(".watermark-layer .wm-text").eachText()).containsOnly("CONFIDENTIAL").hasSize(3);
        assertThat(render(List.of(id)).body().hasClass("with-watermark")).isFalse();
    }

    @Test
    void picturesAreEmbeddedAndNothingIsFetchedFromTheNetwork() {
        Check id = ReportFixtures.check("AADHAAR", "identity", "Identity", CheckStatus.VERIFIED);
        documents.attach(id.id(), "image/png", ReportFixtures.png(300, 200, Color.WHITE), false, false, null);
        var photo = documents.photo(ReportFixtures.png(100, 130, Color.GRAY));
        String raw = renderer.render(assembler.assemble(ReportFixtures.report(List.of(id), photo)), false);
        Document html = Jsoup.parse(raw);

        assertThat(html.select("#page1 .photo-wrap img").attr("src")).startsWith("data:image/png;base64,");
        assertThat(html.select(".doc-frame .doc-img-wrap img").attr("src")).startsWith("data:image/png;base64,");
        assertThat(html.select(".doc-frame-header").text()).isEqualTo("Document 1 — Verifies Identity — Original Document");
        // no external resources: every src is a data URI, and the CSS does not import anything
        assertThat(html.select("[src]").eachAttr("src")).allMatch(src -> src.startsWith("data:"));
        assertThat(html.select("link")).isEmpty();
        assertThat(raw).doesNotContain("@import").doesNotContain("http://fonts").doesNotContain("https://fonts");
    }

    @Test
    void theFontIsBundledAndTheReferenceStylesArePresent() {
        String raw = renderer.render(assembler.assemble(ReportFixtures.report(ReportFixtures.manyGroups(1), null)), false);
        assertThat(raw).contains("font-family:'Inter'").contains("data:font/woff2;base64,");
        assertThat(raw).contains(".detail-section-title").contains(".doc-frame-large").contains("@page { size:A4; margin:0; }");
        assertThat(raw).doesNotContain(".sidebar{").as("the editor's sidebar styles are not in a generated report");
    }

    @Test
    void previewAddsTheDeskBackgroundAndThePdfDoesNot() {
        String preview = renderer.render(assembler.assemble(ReportFixtures.report(ReportFixtures.manyGroups(1), null)), true);
        String pdf = renderer.render(assembler.assemble(ReportFixtures.report(ReportFixtures.manyGroups(1), null)), false);
        assertThat(preview).contains("background:#eef3f5");
        assertThat(pdf).doesNotContain("background:#eef3f5");
    }

    @Test
    void noTemplateMarkupIsLeftOver() {
        Document html = render(ReportFixtures.manyGroups(5));
        String raw = html.outerHtml();
        assertThat(raw).doesNotContainPattern("\sth:[a-z]+=").doesNotContain("[[").doesNotContain("${");
        assertThat(html.select("#pagesArea > .page")).hasSize(8);
    }

    @Test
    void movedDocumentsGetOwnPagesWithALargerBoxWhenAsked() {
        Check id = ReportFixtures.check("AADHAAR", "identity", "Identity", CheckStatus.VERIFIED);
        documents.attach(id.id(), "image/png", ReportFixtures.png(300, 200, Color.WHITE), true, true, null);
        Document html = render(ReportFixtures.report(List.of(id), null), false);
        var pages = html.select("#pagesArea > .page");
        assertThat(pages).hasSize(4);
        assertThat(pages.get(2).select(".doc-frame-large")).hasSize(1);
        assertThat(pages.get(2).text()).contains("Continued");
        assertThat(pages.get(1).select(".doc-frame")).as("the detail page has no frame for a moved document").isEmpty();
    }
}
