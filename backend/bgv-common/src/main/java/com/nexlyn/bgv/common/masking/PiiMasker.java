package com.nexlyn.bgv.common.masking;

/**
 * Shows enough of an identifier to recognise it and hides the rest (CLAUDE.md {@literal §11.4}):
 * Aadhaar {@code XXXX XXXX 1234}, PAN {@code ABXXXXX12F}, anything else {@code XXXXXXXX1234}.
 * Every response masks sensitive values by default; revealing one needs a permission and is audited.
 */
public final class PiiMasker {

    private PiiMasker() {
    }

    /** {@code XXXX XXXX 1234}. Expects the 12 digits; anything else falls back to the generic mask. */
    public static String maskAadhaar(String aadhaar) {
        String digits = aadhaar == null ? "" : aadhaar.replaceAll("[\\s-]", "");
        return digits.length() == 12 ? "XXXX XXXX " + digits.substring(8) : maskGeneric(digits);
    }

    /** {@code ABXXXXX12F}: the first two and last three characters stay. */
    public static String maskPan(String pan) {
        String value = pan == null ? "" : pan.trim();
        return value.length() == 10 ? value.substring(0, 2) + "XXXXX" + value.substring(7) : maskGeneric(value);
    }

    /** Every character hidden except the last four. */
    public static String maskGeneric(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        int keep = Math.min(4, value.length() / 2); // never show more than half of a short value
        return "X".repeat(value.length() - keep) + value.substring(value.length() - keep);
    }

    /** The last four characters, kept next to the encrypted value for display and lookups. */
    public static String last4(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 4 ? value : value.substring(value.length() - 4);
    }
}
