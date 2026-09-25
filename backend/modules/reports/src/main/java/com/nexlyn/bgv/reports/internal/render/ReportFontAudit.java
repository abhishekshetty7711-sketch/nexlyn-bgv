package com.nexlyn.bgv.reports.internal.render;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Looks at the fonts inside a finished report PDF. The report's fonts are bundled with the application (Inter for the
 * text, Selawik for the brand block, the title and the footer, see static/report/fonts/README.txt); if the browser
 * cannot use one of them it silently draws that text in whatever font the machine has (Liberation Sans in the Linux
 * container, Segoe UI or Arial on Windows), and the report looks different from one computer to another. The PDF
 * records exactly which font drew what, so that is where such a change is caught.
 */
public final class ReportFontAudit {

    /** The families the report may be printed in. Names of indic-script fonts are added by callers that print such text. */
    public static final Set<String> BUNDLED = Set.of("Inter", "Selawik");

    /**
     * Words of the brand block, the title and the footer, and the family each must be printed in. Body text (Inter)
     * is checked through the list of allowed families.
     */
    public static final Map<String, String> BRAND_WORDS = Map.of(
            "NEXLYN", "Selawik", "SERVICES", "Selawik", "VERIFY", "Selawik", "VALIDATE", "Selawik", "TRUST", "Selawik",
            "Verification", "Selawik", "Page", "Selawik");

    private ReportFontAudit() {
    }

    /** The fonts of the PDF (family names, e.g. "Inter", "Selawik") and what is wrong with them, in plain words. */
    public record Result(Set<String> families, List<String> problems) {
        public boolean ok() {
            return problems.isEmpty();
        }
    }

    /** "ABCDEF+Selawik-Bold" -> "Selawik"; "AAAAAA+LiberationSans" -> "LiberationSans". */
    static String family(String baseName) {
        if (baseName == null) {
            return "(unnamed font)";
        }
        String name = baseName.substring(baseName.indexOf('+') + 1);
        int dash = name.indexOf('-');
        return dash > 0 ? name.substring(0, dash) : name;
    }

    /** Every font of every page, embedded or not, and whether each is one of {@code allowedFamilies}. */
    public static Result inspect(byte[] pdf, Collection<String> allowedFamilies) {
        Set<String> families = new LinkedHashSet<>();
        List<String> problems = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            Set<Object> visited = new LinkedHashSet<>();
            for (int i = 0; i < document.getNumberOfPages(); i++) {
                collect(document.getPage(i).getResources(), families, problems, allowedFamilies, visited);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("The report PDF could not be read for the font check", e);
        }
        return new Result(families, problems);
    }

    private static void collect(PDResources resources, Set<String> families, List<String> problems, Collection<String> allowed, Set<Object> visited)
            throws IOException {
        if (resources == null || !visited.add(resources.getCOSObject())) {
            return;
        }
        for (COSName name : resources.getFontNames()) {
            PDFont font = resources.getFont(name);
            String family = family(font.getName());
            families.add(family);
            if (!font.isEmbedded()) {
                problem(problems, "Font " + font.getName() + " is not embedded in the PDF (the viewer would substitute its own)");
            }
            if (!allowed.contains(family)) {
                problem(problems, "Font " + font.getName() + " is not one of the bundled report fonts " + allowed
                        + ": the browser did not find the bundled font and used another one");
            }
        }
        for (COSName name : resources.getXObjectNames()) {
            PDXObject xObject = resources.getXObject(name);
            if (xObject instanceof PDFormXObject form) {
                collect(form.getResources(), families, problems, allowed, visited);
            }
        }
    }

    private static void problem(List<String> problems, String text) {
        if (!problems.contains(text)) {
            problems.add(text);
        }
    }

    /**
     * The family that drew the first occurrence of each word on the given page (1 based). The glyphs of the page are
     * read in drawing order and blanks are ignored, so words that are letter-spaced (the brand block) or split into
     * several text runs by the browser are found too.
     */
    public static Map<String, String> familiesOfWords(byte[] pdf, int page, Collection<String> words) {
        StringBuilder compact = new StringBuilder();
        List<TextPosition> owners = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper() {
                @Override
                protected void processTextPosition(TextPosition position) {
                    for (char c : position.getUnicode().toCharArray()) {
                        if (!Character.isWhitespace(c)) {
                            compact.append(c);
                            owners.add(position);
                        }
                    }
                    super.processTextPosition(position);
                }
            };
            stripper.setStartPage(page);
            stripper.setEndPage(page);
            stripper.getText(document);
        } catch (IOException e) {
            throw new UncheckedIOException("The report PDF could not be read for the font check", e);
        }
        Map<String, String> found = new LinkedHashMap<>();
        for (String word : words) {
            int at = compact.indexOf(word);
            if (at >= 0) {
                found.put(word, family(owners.get(at).getFont().getName()));
            }
        }
        return found;
    }

    /**
     * The whole check on a print of {@link HtmlRenderer#fontCheckPage()}: only bundled fonts, all embedded, and the
     * brand block, title and footer really drawn in Selawik. Returns the problems (empty when all is well).
     */
    public static List<String> checkSamplePage(byte[] pdf) {
        List<String> problems = new ArrayList<>(inspect(pdf, BUNDLED).problems());
        Map<String, String> drawn = familiesOfWords(pdf, 1, BRAND_WORDS.keySet());
        BRAND_WORDS.forEach((word, expected) -> {
            String actual = drawn.get(word);
            if (actual == null) {
                problems.add("The word \"" + word + "\" was not found on the sample page");
            } else if (!expected.equals(actual)) {
                problems.add("\"" + word + "\" is drawn in " + actual + ", expected " + expected);
            }
        });
        return problems;
    }
}
