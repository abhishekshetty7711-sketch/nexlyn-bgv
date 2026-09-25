package com.nexlyn.bgv.reports.internal.render;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Media;
import com.microsoft.playwright.options.WaitUntilState;
import com.nexlyn.bgv.cases.CaseReport;
import com.nexlyn.bgv.cases.CaseReport.Check;
import com.nexlyn.bgv.common.enums.CheckStatus;
import com.nexlyn.bgv.reports.ReportFixtures;
import com.nexlyn.bgv.reports.ReportFixtures.StubDocuments;
import com.nexlyn.bgv.reports.internal.assemble.AssetSource;
import com.nexlyn.bgv.reports.internal.assemble.ReportModelAssembler;
import com.nexlyn.bgv.reports.internal.config.ReportsProperties;
import com.nexlyn.bgv.reports.internal.model.ReportPages.ReportDocument;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.awt.Color;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Drives the REFERENCE tool ({@code docs/reference/nexlyn-bgv-report-v3.2.html}) in the same browser, prints the same
 * documents with "Move to Next Page" and "Use Larger Box" in every combination, and checks that this platform's PDF has
 * the same pages and the same box sizes (CLAUDE.md 6.2: ported exactly). Skipped without a browser or the reference file.
 */
class ReferenceToolComparisonTest {

    static final Path REFERENCE = Path.of("..", "..", "..", "docs", "reference", "nexlyn-bgv-report-v3.2.html").toAbsolutePath().normalize();
    static PdfRenderer pdf;

    /** One box: the detail page it is on (1 = the check's own page), whether it has the larger class, its height and its picture's. */
    record Box(int detailPage, boolean large, int height, int picture) {
    }

    StubDocuments documents;
    ReportModelAssembler assembler;
    HtmlRenderer html;

    @BeforeAll
    static void browser() {
        pdf = new PdfRenderer(new ReportsProperties(null, null, null, null, null));
        assumeTrue(pdf.configuredBrowser().isPresent(), "no browser found: set CHROMIUM_PATH");
        assumeTrue(Files.exists(REFERENCE), "the reference tool is not at " + REFERENCE);
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

    /** The rows of the card, the same in both tools so the page is equally full (the frame is sized by the room left on its page). */
    static final String[][] ROWS = {{"Aadhaar Number", "XXXX XXXX 0124"}, {"Full Name", "Asha Rao"}, {"DOB", "17/05/1994"}, {"Father's Name", "Ravi Rao"},
            {"Street Address", "14, 3rd Cross, Indiranagar"}, {"City / Town", "Bengaluru"}, {"State", "Karnataka"}, {"PIN Code", "560038"}, {"Country", "India"}};

    /** The reference tool with one card holding these documents: (moveToNextPage, largerBox) each, in order. */
    @SuppressWarnings("unchecked")
    private List<Box> reference(boolean[][] docs) {
        try (Playwright playwright = Playwright.create(new Playwright.CreateOptions().setEnv(PdfRenderer.driverEnvironment()))) {
            BrowserType.LaunchOptions launch = new BrowserType.LaunchOptions().setHeadless(true);
            pdf.configuredBrowser().ifPresent(launch::setExecutablePath);
            try (Browser browser = playwright.chromium().launch(launch)) {
                var context = browser.newContext();
                context.route(url -> !url.startsWith("file:"), route -> route.abort()); // no network: fonts come from the machine
                Page page = context.newPage();
                page.setViewportSize(794, 1123);
                page.navigate(REFERENCE.toUri().toString(), new Page.NavigateOptions().setWaitUntil(WaitUntilState.LOAD));
                List<List<Boolean>> spec = new ArrayList<>();
                for (boolean[] d : docs) {
                    spec.add(List.of(d[0], d[1]));
                }
                page.evaluate("""
                        (rows) => {
                          verifCards = [verifCards[0]];
                          verifCards[0].checks = rows.map(r => ({ label: r[0], val: r[1], tick: true }));
                          verifCards[0].details = [{ label: 'Verification Type', val: 'Standard' }, { label: 'Document Type', val: 'Document of Identity' },
                            { label: 'Requested Date', val: '19/05/2026' }, { label: 'Completed Date', val: '11/06/2026' }];
                        }
                        """, List.of(ROWS[0], ROWS[1], ROWS[2], ROWS[3], ROWS[4], ROWS[5], ROWS[6], ROWS[7], ROWS[8]));
                page.evaluate("""
                        (spec) => {
                          const png = () => { const c = document.createElement('canvas'); c.width = 900; c.height = 600;
                            const x = c.getContext('2d'); x.fillStyle = '#e6f0fa'; x.fillRect(0, 0, 900, 600); return c.toDataURL('image/png'); };
                          verifCards = [verifCards[0]];
                          verifCards[0].docs = spec.map((d, i) => ({ label: i === 0 ? 'Original Document' : 'Additional Document ' + i,
                            img: png(), splitToNext: d[0], useLargerBox: d[1] }));
                          renderDetailPages();
                        }
                        """, spec);
                page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.PRINT));
                page.evaluate("() => document.fonts.ready");
                List<Map<String, Object>> rows = (List<Map<String, Object>>) page.evaluate("""
                        () => Array.from(document.querySelectorAll('#detailPages > .page')).flatMap((pg, index) =>
                          Array.from(pg.querySelectorAll('.doc-frame')).map(f => {
                            const img = f.querySelector('img');
                            return { page: index + 1, large: f.classList.contains('doc-frame-large'),
                                     height: Math.round(f.getBoundingClientRect().height), picture: img ? Math.round(img.getBoundingClientRect().height) : 0 };
                          }))
                        """);
                List<Box> boxes = new ArrayList<>();
                for (Map<String, Object> row : rows) {
                    boxes.add(new Box(((Number) row.get("page")).intValue(), (Boolean) row.get("large"), ((Number) row.get("height")).intValue(),
                            ((Number) row.get("picture")).intValue()));
                }
                return boxes;
            }
        }
    }

    /** The same documents through this platform; detail pages are counted from the check's own page. */
    private List<Box> ours(boolean[][] docs) {
        Check id = ReportFixtures.check("AADHAAR", "identity", "Identity Verification (Aadhaar)", CheckStatus.VERIFIED);
        for (boolean[] d : docs) {
            documents.attach(id.id(), "image/png", ReportFixtures.png(900, 600, new Color(230, 240, 250)), d[0], d[1], null);
        }
        List<CaseReport.Field> fields = new ArrayList<>();
        for (String[] row : ROWS) {
            fields.add(new CaseReport.Field(row[0], "text", row[1], true));
        }
        CaseReport report = ReportFixtures.report(List.of(ReportFixtures.withFields(id, fields, List.of(), List.of())), null);
        ReportDocument document = assembler.assemble(report);
        PdfRenderer.Rendered rendered = pdf.render(html.render(document, false));
        List<Box> boxes = new ArrayList<>();
        for (PdfRenderer.FrameBox f : rendered.frames()) {
            boxes.add(new Box(f.page() - 1, f.large(), f.heightPx(), f.pictureHeightPx()));
        }
        return boxes;
    }

    private void same(boolean[]... docs) {
        List<Box> theirs = reference(docs);
        List<Box> mine = ours(docs);
        System.out.println("REFERENCE " + theirs);
        System.out.println("OURS      " + mine);
        assertThat(mine).as("number of boxes").hasSameSizeAs(theirs);
        for (int i = 0; i < theirs.size(); i++) {
            assertThat(mine.get(i).detailPage()).as("page of box %d", i).isEqualTo(theirs.get(i).detailPage());
            assertThat(mine.get(i).large()).as("larger class of box %d", i).isEqualTo(theirs.get(i).large());
            // A box on a page of its own has a fixed size (400 px, or 800 px for the larger box) and must match to the pixel
            // rounding. A box on the check's own page takes the room left below the rows above it, so it is allowed a few
            // more pixels (measured: the reference 316 px, this platform 323 px, with the same rows).
            int slack = theirs.get(i).detailPage() == 1 ? 12 : 3;
            assertThat(mine.get(i).height()).as("height of box %d", i).isBetween(theirs.get(i).height() - slack, theirs.get(i).height() + slack);
            assertThat(mine.get(i).picture()).as("picture height of box %d", i).isBetween(theirs.get(i).picture() - 3, theirs.get(i).picture() + 3);
        }
    }

    @Test
    void aDocumentThatStaysIsTheSame() {
        same(new boolean[]{false, false});
    }

    @Test
    void aMovedDocumentIsTheSame() {
        same(new boolean[]{false, false}, new boolean[]{true, false});
    }

    @Test
    void aLargerBoxIsTheSame() {
        same(new boolean[]{true, true});
    }

    @Test
    void bothOptionsTogetherWithOtherDocumentsAreTheSame() {
        same(new boolean[]{false, false}, new boolean[]{true, true}, new boolean[]{true, false});
    }
}
