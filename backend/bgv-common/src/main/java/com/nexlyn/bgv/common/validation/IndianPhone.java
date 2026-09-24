package com.nexlyn.bgv.common.validation;

import java.util.regex.Pattern;

/**
 * Indian mobile numbers. Accepts the ways people type them (spaces, dashes, brackets, a leading
 * 0, 91 or +91) and reduces them to one stored form, {@code +91XXXXXXXXXX}. The number itself must be
 * 10 digits starting 6 to 9. Display form is {@code +91 XXXXX XXXXX} (CLAUDE.md {@literal §6.2}).
 */
public final class IndianPhone {

    private static final Pattern MOBILE = Pattern.compile("^[6-9][0-9]{9}$");

    private IndianPhone() {
    }

    /** The stored form, or null if the text is not a valid Indian mobile number. */
    public static String normalize(String input) {
        if (input == null) {
            return null;
        }
        String digits = input.replaceAll("[\\s\\-().]", "");
        if (digits.startsWith("+91")) {
            digits = digits.substring(3);
        } else if (digits.startsWith("0091")) {
            digits = digits.substring(4);
        } else if (digits.startsWith("91") && digits.length() == 12) {
            digits = digits.substring(2);
        } else if (digits.startsWith("0") && digits.length() == 11) {
            digits = digits.substring(1);
        }
        return MOBILE.matcher(digits).matches() ? "+91" + digits : null;
    }

    /** {@code +91 XXXXX XXXXX} for screens and reports; returns the input unchanged if it is not a stored number. */
    public static String format(String stored) {
        if (stored == null || !stored.matches("^\\+91[6-9][0-9]{9}$")) {
            return stored;
        }
        return "+91 " + stored.substring(3, 8) + " " + stored.substring(8);
    }
}
