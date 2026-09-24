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

    private static String buildCss() {
        StringBuilder css = new StringBuilder();
        for (int weight : FONT_WEIGHTS) {
            css.append("@font-face{font-family:'Inter';font-style:normal;font-weight:").append(weight)
                    .append(";font-display:block;src:url(data:font/woff2;base64,")
                    .append(Base64.getEncoder().encodeToString(bytes("static/report/fonts/inter-latin-" + weight + "-normal.woff2")))
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
