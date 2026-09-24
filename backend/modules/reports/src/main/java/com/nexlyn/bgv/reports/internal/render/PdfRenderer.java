package com.nexlyn.bgv.reports.internal.render;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Margin;
import com.microsoft.playwright.options.Media;
import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.common.error.ErrorCode;
import com.nexlyn.bgv.reports.internal.config.ReportsProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Prints the report HTML to an A4 PDF with a headless Chromium (Playwright for Java, CLAUDE.md section 13,
 * step 4). It also checks every page for content that does not fit: the pages have a fixed A4 size, so
 * anything too tall would be cut off or run into the footer, and the analyst must be told which page to
 * fix (usually by moving a document to its own page).
 *
 * <p>Each call starts its own browser and stops it again. Playwright objects must not be shared between
 * threads, and reports are made occasionally, by at most a few at a time (see the job executor), so the
 * start-up cost is a fair price for never sharing state between two people's reports.
 */
@Component
public class PdfRenderer {

    private static final Logger log = LoggerFactory.getLogger(PdfRenderer.class);

    /** A page whose content runs past the space above its footer. {@code overflowPixels} is by how much. */
    public record PageOverflow(int page, int overflowPixels) {
    }

    /** The printed PDF, its number of pages, and the pages whose content did not fit. */
    public record Rendered(byte[] pdf, int pageCount, List<PageOverflow> overflows) {
    }

    /**
     * For each page: the lowest edge of anything visible in it (text, pictures, boxes with a border or a
     * background; plain wrappers, the footer and the watermark do not count) compared with the top of the
     * footer, or the page's own bottom edge when there is no footer.
     */
    private static final String MEASURE_SCRIPT = """
            () => Array.from(document.querySelectorAll('#pagesArea > .page')).map((page, index) => {
              const box = page.getBoundingClientRect();
              const footer = page.querySelector(':scope > .page-footer');
              const limit = footer ? footer.getBoundingClientRect().top : box.bottom;
              let lowest = 0;
              for (const el of page.querySelectorAll('*')) {
                if (el.closest('.page-footer') || el.closest('.watermark-layer')) continue;
                const style = getComputedStyle(el);
                const hasBox = parseFloat(style.borderBottomWidth) > 0 || (style.backgroundColor !== 'rgba(0, 0, 0, 0)' && style.backgroundColor !== 'transparent');
                if (el.children.length > 0 && !hasBox) continue; // a plain wrapper: only what is inside it counts
                const r = el.getBoundingClientRect();
                if (r.height > 0 && r.width > 0) lowest = Math.max(lowest, r.bottom);
              }
              return { page: index + 1, over: Math.round(lowest - limit) };
            })
            """;

    /** Tolerance in CSS pixels: sub-pixel rounding must not raise false alarms. */
    private static final int TOLERANCE = 3;

    private final ReportsProperties properties;

    public PdfRenderer(ReportsProperties properties) {
        this.properties = properties;
    }

    /** The browser this machine would use, or empty when none is configured or found (Playwright's own is then used). */
    public Optional<Path> configuredBrowser() {
        return BrowserLocator.find(properties.chromiumPath());
    }

    /**
     * Prints the report. A browser that fails once (memory pressure on a busy machine can make "printing
     * failed" appear for no good reason) is given one more try with a fresh browser before the job fails.
     */
    public Rendered render(String html) {
        try {
            return renderOnce(html);
        } catch (ApiException first) {
            log.warn("The first attempt to print a report failed; trying once more");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw first;
            }
            return renderOnce(html);
        }
    }

    private Rendered renderOnce(String html) {
        Optional<Path> browserPath = configuredBrowser();
        Map<String, String> env = browserPath.isPresent() ? Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1") : Map.of();
        try (Playwright playwright = Playwright.create(new Playwright.CreateOptions().setEnv(env))) {
            BrowserType.LaunchOptions launch = new BrowserType.LaunchOptions().setHeadless(true);
            browserPath.ifPresent(launch::setExecutablePath);
            if (properties.noSandbox()) {
                launch.setArgs(List.of("--no-sandbox"));
            }
            try (Browser browser = playwright.chromium().launch(launch)) {
                Page page = browser.newPage();
                page.setViewportSize(794, 1123); // A4 at 96 dpi
                page.setContent(html, new Page.SetContentOptions().setWaitUntil(com.microsoft.playwright.options.WaitUntilState.LOAD));
                page.emulateMedia(new Page.EmulateMediaOptions().setMedia(Media.PRINT));
                page.evaluate("() => document.fonts.ready");

                List<PageOverflow> overflows = new ArrayList<>();
                Object measured = page.evaluate(MEASURE_SCRIPT);
                if (measured instanceof List<?> list) {
                    for (Object item : list) {
                        if (item instanceof Map<?, ?> row) {
                            int over = ((Number) row.get("over")).intValue();
                            if (over > TOLERANCE) {
                                overflows.add(new PageOverflow(((Number) row.get("page")).intValue(), over));
                            }
                        }
                    }
                }

                byte[] pdf = page.pdf(new Page.PdfOptions()
                        .setFormat("A4")
                        .setPrintBackground(true)
                        .setPreferCSSPageSize(true)
                        .setMargin(new Margin().setTop("0").setRight("0").setBottom("0").setLeft("0")));
                int count = page.evaluate("() => document.querySelectorAll('#pagesArea > .page').length") instanceof Number n ? n.intValue() : 0;
                return new Rendered(pdf, count, List.copyOf(overflows));
            }
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("The PDF renderer failed: {}", e.toString());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "The PDF could not be made right now. Please try again in a moment.");
        }
    }
}
