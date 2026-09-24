package com.nexlyn.bgv.common.validation;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Remarks may contain bold text and nothing else (CLAUDE.md {@literal §6.2}). This keeps
 * {@code <strong>} and {@code <b>} (written back as {@code <strong>}) and turns every other character
 * that HTML treats specially into an entity, so a script or any other tag can never survive.
 * Unbalanced tags are repaired. Running it twice gives the same result, so text can be edited and
 * saved repeatedly without the entities piling up.
 */
public final class BoldOnlyHtml {

    private static final Pattern BOLD_TAG = Pattern.compile("(?i)<(/?)(?:strong|b)\\s*>");
    /** An ampersand that is not already the start of one of the entities this class writes. */
    private static final Pattern BARE_AMPERSAND = Pattern.compile("&(?!(?:amp|lt|gt|quot|#39);)");

    private BoldOnlyHtml() {
    }

    public static String sanitize(String input) {
        if (input == null) {
            return null;
        }
        StringBuilder out = new StringBuilder(input.length() + 16);
        Matcher tags = BOLD_TAG.matcher(input);
        int last = 0;
        int open = 0;
        while (tags.find()) {
            out.append(escape(input.substring(last, tags.start())));
            if (tags.group(1).isEmpty()) {
                out.append("<strong>");
                open++;
            } else if (open > 0) {
                out.append("</strong>");
                open--;
            } // a closing tag with nothing open is dropped
            last = tags.end();
        }
        out.append(escape(input.substring(last)));
        out.append("</strong>".repeat(open));
        return out.toString();
    }

    private static String escape(String text) {
        return BARE_AMPERSAND.matcher(text).replaceAll("&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
