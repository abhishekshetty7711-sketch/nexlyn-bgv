package com.nexlyn.bgv.reports.internal.render;

import com.nexlyn.bgv.reports.internal.assemble.AssetSource;
import com.nexlyn.bgv.reports.internal.model.ReportPages.ReportDocument;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

/**
 * Turns a {@link ReportDocument} into one self-contained HTML page (CLAUDE.md section 13, step 3): fonts,
 * styles and pictures are all inside it, so it prints the same in the preview and in the PDF renderer and
 * needs nothing from the network. Every text goes through Thymeleaf's escaping; the only raw HTML is the
 * bold-only remarks (sanitised earlier) and the report's own icons.
 */
@Component
public class HtmlRenderer {

    /** Inter, the report's font (CLAUDE.md section 6.2), bundled so no font is ever fetched at print time. */
    private static final List<Integer> FONT_WEIGHTS = List.of(400, 500, 600, 700, 800, 900);

    /**
     * Selawik, the font of the brand block, the report title and the footer: (file weight, CSS weights it serves).
     * There is no black weight, so 800 and 900 use the bold face.
     */
    private static final List<int[]> SELAWIK = List.of(new int[]{400, 300, 500}, new int[]{600, 600, 600}, new int[]{700, 700, 900});

    /** Extra rules for the on-screen preview only: pages on a grey desk with a little air around them. */
    private static final String PREVIEW_CSS = "body{background:#eef3f5;padding:24px 0;}@media print{body{background:#fff;padding:0;}}";

    /** The Thymeleaf helper the templates call for icons. */
    public static final class IconHelper {
        public String card(String key) {
            return Icons.card(key);
        }

        public String status(String key) {
            return Icons.status(key);
        }
    }

    private final TemplateEngine engine;
    private final AssetSource assets;
    private final String baseCss = buildCss();

    public HtmlRenderer(TemplateEngine engine, AssetSource assets) {
        this.engine = engine;
        this.assets = assets;
    }

    /** @param preview add the on-screen desk styling (false for the PDF, which prints the pages edge to edge) */
    public String render(ReportDocument document, boolean preview) {
        Context context = new Context();
        context.setVariable("doc", document);
        context.setVariable("css", preview ? baseCss + PREVIEW_CSS : baseCss);
        context.setVariable("logo", assets.logoDataUri());
        context.setVariable("icons", new IconHelper());
        context.setVariable("services", ServicesCatalog.SERVICES);
        return engine.process("report/report", context);
    }

    /**
     * A one-page sample with the words of the brand block, the report title, a footer and body text, in the same styles
     * and with the same bundled fonts as a report. The font self-check prints it and looks at the fonts in the PDF.
     */
    public String fontCheckPage() {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><style>" + baseCss + "</style></head><body>"
                + "<div id=\"pagesArea\"><div class=\"page\" id=\"page1\">"
                + "<a class=\"brand\"><span class=\"wm\"><span class=\"lo\">NEXLYN</span><span class=\"svc-txt-plain\">SERVICES</span>"
                + "<span class=\"tagline\">VERIFY <span class=\"sep\"></span> VALIDATE <span class=\"sep\"></span> TRUST</span></span></a>"
                + "<div class=\"report-title\">Verification <span>Report</span></div>"
                + "<p>Body text of the report: Asha Rao, 14, 3rd Cross, Bengaluru 560038 - 0123456789</p>"
                + "<p><b>Bold body text</b> and <span style=\"font-weight:500\">medium body text</span> and <span style=\"font-weight:600\">semibold</span> and <span style=\"font-weight:800\">extra bold</span></p>"
                + "<div class=\"page-footer\"><div class=\"pf-legend-row\"><span class=\"pf-leg\">Verified</span><span class=\"pf-leg\">Discrepancy</span></div>"
                + "<div class=\"pf-bottom\"><div class=\"pf-logo\"><span style=\"font-size:13px;font-weight:900;letter-spacing:.06em;color:#0c2d6b;line-height:1;\">NEXLYN</span></div>"
                + "<div class=\"pf-pagenum\">Page <strong>1</strong> of <strong>1</strong></div></div></div>"
                + "</div></div></body></html>";
    }

    private static String buildCss() {
        StringBuilder css = new StringBuilder();
        for (int weight : FONT_WEIGHTS) {
            css.append("@font-face{font-family:'Inter';font-style:normal;font-weight:").append(weight)
                    .append(";font-display:block;src:url(data:font/woff2;base64,")
                    .append(Base64.getEncoder().encodeToString(bytes("static/report/fonts/inter-latin-" + weight + "-normal.woff2")))
                    .append(") format('woff2');}\n");
        }
        for (int[] face : SELAWIK) {
            css.append("@font-face{font-family:'Selawik';font-style:normal;font-weight:").append(face[1]).append(' ').append(face[2])
                    .append(";font-display:block;src:url(data:font/woff2;base64,")
                    .append(Base64.getEncoder().encodeToString(bytes("static/report/fonts/selawik-" + face[0] + "-normal.woff2")))
                    .append(") format('woff2');}\n");
        }
        css.append(new String(bytes("static/report/report.css"), StandardCharsets.UTF_8));
        return css.toString();
    }

    private static byte[] bytes(String path) {
        try (var in = new ClassPathResource(path).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("Missing bundled report file " + path, e);
        }
    }
}
