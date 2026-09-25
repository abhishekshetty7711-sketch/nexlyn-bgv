package com.nexlyn.bgv.reports.internal.render;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * At start-up, prints a small sample page with the real browser of THIS machine and checks the fonts in the PDF
 * ({@link ReportFontAudit}). It answers "would a report printed here use the report's own fonts?" for the place that
 * really matters, the container the application runs in, before anybody prints a real report.
 *
 * <p>{@code nexlyn.reports.font-check} (environment variable NEXLYN_REPORTS_FONT_CHECK): {@code off} (the default, for
 * development), {@code warn} (log an error and go on) or {@code fail} (the application does not start). The Docker
 * image sets {@code fail}: an image whose reports would come out in the wrong font must not go live.
 */
@Component
public class ReportFontSelfCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReportFontSelfCheck.class);

    private final HtmlRenderer html;
    private final PdfRenderer pdf;
    private final String mode;

    public ReportFontSelfCheck(HtmlRenderer html, PdfRenderer pdf, @Value("${nexlyn.reports.font-check:off}") String mode) {
        this.html = html;
        this.pdf = pdf;
        this.mode = mode == null ? "off" : mode.trim().toLowerCase();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (mode.equals("off")) {
            return;
        }
        List<String> problems = check();
        if (problems.isEmpty()) {
            log.info("Report font check passed: the brand block, title, footer and text are printed in the bundled fonts");
            return;
        }
        String message = "Report font check FAILED, reports printed here would not use the bundled fonts: " + String.join("; ", problems);
        if (mode.equals("fail")) {
            throw new IllegalStateException(message);
        }
        log.error(message);
    }

    /** The problems found by printing the sample page here (empty when all is well). */
    List<String> check() {
        try {
            return ReportFontAudit.checkSamplePage(pdf.render(html.fontCheckPage()).pdf());
        } catch (RuntimeException e) {
            return List.of("The sample page could not be printed with this machine's browser: " + e.getMessage());
        }
    }
}
