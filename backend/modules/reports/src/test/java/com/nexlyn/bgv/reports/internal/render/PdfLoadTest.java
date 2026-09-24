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
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * How long real reports take to print, and whether several at once still work. NOT part of the normal build
 * (it takes minutes): run it by hand, see docs/runbooks/pdf-load.md.
 * <pre>
 * ./mvnw -pl modules/reports -am test -Dtest=PdfLoadTest -Dtest.excluded.groups=none -Dgroups=load -Dsurefire.failIfNoSpecifiedTests=false
 * </pre>
 * The case is a heavy one: 8 checks, two large photographic scans each (noise makes the JPEGs as big as real scans).
 * Numbers go to {@code target/pdf-load.txt}.
 */
@Tag("load")
class PdfLoadTest {

    private static final int REPORTS = Integer.getInteger("load.reports", 12);
    private static final int CONCURRENCY = Integer.getInteger("load.concurrency", 2);

    @Test
    void printsManyHeavyReportsWithTheProductionConcurrency() throws Exception {
        PdfRenderer pdf = new PdfRenderer(new ReportsProperties(null, null, null, null, null));
        assumeTrue(pdf.configuredBrowser().isPresent(), "no Chrome / Chromium / Edge found: set CHROMIUM_PATH");

        StubDocuments documents = new StubDocuments();
        AssetSource assets = new AssetSource();
        ReportModelAssembler assembler = new ReportModelAssembler(documents, new ImageEmbedder(2400), assets);
        SpringTemplateEngine engine = new SpringTemplateEngine();
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");
        engine.setTemplateResolver(resolver);
        HtmlRenderer html = new HtmlRenderer(engine, assets);

        CaseReport report = heavyCase(documents);

        // one to warm up (first start of the browser, class loading), not counted
        Rendered warm = one(assembler, html, pdf, report);

        ExecutorService pool = Executors.newFixedThreadPool(CONCURRENCY);
        List<Callable<Rendered>> jobs = new ArrayList<>();
        for (int i = 0; i < REPORTS; i++) {
            jobs.add(() -> one(assembler, html, pdf, report));
        }
        long start = System.nanoTime();
        List<Future<Rendered>> results = pool.invokeAll(jobs);
        long wallMs = (System.nanoTime() - start) / 1_000_000;
        pool.shutdown();

        List<Long> times = new ArrayList<>();
        int failed = 0;
        for (Future<Rendered> f : results) {
            try {
                times.add(f.get().millis);
            } catch (Exception e) {
                failed++;
            }
        }
        times.sort(Long::compare);
        Runtime rt = Runtime.getRuntime();
        String summary = """
                Heavy case: %d pages, %d KB of PDF, %d KB of HTML (the same case %d times, %d at a time)
                Warm-up render:      %d ms
                Renders ok / failed: %d / %d
                Per render (ms):     min %d, median %d, p95 %d, max %d
                Whole batch:         %d ms  =>  %.1f reports per minute
                Test JVM heap used:  %d MB (report printing itself runs in the browser process, not in this JVM)
                """.formatted(warm.pages, warm.pdfBytes / 1024, warm.htmlBytes / 1024, REPORTS, CONCURRENCY,
                warm.millis, times.size(), failed,
                times.isEmpty() ? 0 : times.get(0),
                times.isEmpty() ? 0 : times.get(times.size() / 2),
                times.isEmpty() ? 0 : times.get(Math.min(times.size() - 1, (int) Math.ceil(times.size() * 0.95) - 1)),
                times.isEmpty() ? 0 : times.get(times.size() - 1),
                wallMs, REPORTS * 60_000.0 / wallMs,
                (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
        System.out.println(summary);
        Files.writeString(Path.of("target", "pdf-load.txt"), summary);

        assertThat(failed).as("every render must succeed").isZero();
        assertThat(times.get(times.size() - 1)).as("no single report may take more than a minute").isLessThan(60_000);
    }

    private record Rendered(long millis, int pages, int pdfBytes, int htmlBytes) {
    }

    private static Rendered one(ReportModelAssembler assembler, HtmlRenderer html, PdfRenderer pdf, CaseReport report) {
        long t = System.nanoTime();
        ReportDocument document = assembler.assemble(report);
        String page = html.render(document, false);
        PdfRenderer.Rendered rendered = pdf.render(page);
        assertThat(rendered.pageCount()).isEqualTo(document.total());
        return new Rendered((System.nanoTime() - t) / 1_000_000, rendered.pageCount(), rendered.pdf().length, page.length());
    }

    private static CaseReport heavyCase(StubDocuments documents) throws IOException {
        String[][] types = {
                {"AADHAAR", "identity", "Identity Verification (Aadhaar)"},
                {"PAN", "identity", "Identity Verification (PAN)"},
                {"COURT", "court", "Court Record (Permanent Address)"},
                {"ADDRESS", "address", "Address Verification"},
                {"EMPLOYMENT", "employment", "Employment Verification"},
                {"EDUCATION", "education", "Education Verification"},
                {"UAN", "unique", "UAN Check"},
                {"CREDIT", "unique", "Credit Check"}};
        List<Check> checks = new ArrayList<>();
        for (String[] t : types) {
            Check check = ReportFixtures.check(t[0], t[1], t[2], CheckStatus.VERIFIED);
            documents.attach(check.id(), "image/jpeg", scan(1800, 1300, t[0].hashCode()), false, false, null);
            documents.attach(check.id(), "image/jpeg", scan(1500, 2000, t[0].hashCode() + 1), true, true, null);
            checks.add(t[0].equals("COURT")
                    ? ReportFixtures.withAttestation(ReportFixtures.withRemarks(check, "No record found."), "", "")
                    : check);
        }
        return ReportFixtures.report(checks, documents.photo(scan(300, 380, 7)));
    }

    /** A noisy picture: as large as a photographed document once compressed. */
    private static byte[] scan(int width, int height, long seed) throws IOException {
        Random random = new Random(seed);
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int base = 200 + random.nextInt(40);
                image.setRGB(x, y, (base << 16) | ((base - random.nextInt(30)) << 8) | (base - random.nextInt(50)));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }
}
