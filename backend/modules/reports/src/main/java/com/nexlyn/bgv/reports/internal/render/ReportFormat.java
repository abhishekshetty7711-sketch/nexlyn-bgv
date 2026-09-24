package com.nexlyn.bgv.reports.internal.render;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexlyn.bgv.common.enums.CheckStatus;
import com.nexlyn.bgv.reports.internal.model.ReportPages.Pill;
import com.nexlyn.bgv.reports.internal.model.ReportPages.Status;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * How values print on the report: dates (numeric or with a month name), yes/no, repeated rows, the
 * status badges and the page-1 pill (all ported from the reference tool, CLAUDE.md section 6.2).
 */
public final class ReportFormat {

    /** Shown where a cover value is missing, like the reference tool. */
    public static final String DASH = "—";

    private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};
    private static final ObjectMapper JSON = new ObjectMapper();

    private ReportFormat() {
    }

    // ---- dates ---------------------------------------------------------------------------------------

    /** 11/06/2026 (NUMERIC) or 11-Jun-2026 (TEXT). A missing date prints as an empty string. */
    public static String date(LocalDate date, String dateFormat) {
        if (date == null) {
            return "";
        }
        String day = String.format(Locale.ROOT, "%02d", date.getDayOfMonth());
        if ("TEXT".equals(dateFormat)) {
            return day + "-" + MONTHS[date.getMonthValue() - 1] + "-" + date.getYear();
        }
        return day + "/" + String.format(Locale.ROOT, "%02d", date.getMonthValue()) + "/" + date.getYear();
    }

    /** As {@link #date} but a missing date prints as the dash used on the cover. */
    public static String dateOrDash(LocalDate date, String dateFormat) {
        return date == null ? DASH : date(date, dateFormat);
    }

    public static String orDash(String value) {
        return value == null || value.isBlank() ? DASH : value;
    }

    /** Text that looks like an ISO date becomes a formatted date; anything else is left alone. */
    static String maybeDate(String text, String dateFormat) {
        if (text != null && text.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try {
                return date(LocalDate.parse(text), dateFormat);
            } catch (RuntimeException e) {
                return text;
            }
        }
        return text;
    }

    // ---- numbers -------------------------------------------------------------------------------------

    /** Two digits at least: 2 prints as 02 (Total / Completed on the overview). */
    public static String twoDigits(int number) {
        return String.format(Locale.ROOT, "%02d", number);
    }

    // ---- field values ---------------------------------------------------------------------------------

    /** The text of one row of a check's table. Sensitive numbers arrive masked and are printed as they are. */
    public static String fieldValue(String type, String raw, String dateFormat) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        return switch (type) {
            case "date" -> maybeDate(raw.trim(), dateFormat);
            case "boolean" -> booleanText(raw);
            case "repeatable" -> rows(raw, dateFormat);
            default -> raw;
        };
    }

    private static String booleanText(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("true") || value.equals("yes")) {
            return "Yes";
        }
        if (value.equals("false") || value.equals("no")) {
            return "No";
        }
        return raw;
    }

    /** Repeated rows (for example gap periods) stored as JSON: one line per row, cells separated by a dot. */
    private static String rows(String json, String dateFormat) {
        try {
            List<Map<String, String>> rows = JSON.readValue(json, new TypeReference<>() {
            });
            return rows.stream()
                    .map(row -> row.values().stream()
                            .filter(cell -> cell != null && !cell.isBlank())
                            .map(cell -> maybeDate(cell.trim(), dateFormat))
                            .collect(Collectors.joining(" · ")))
                    .filter(line -> !line.isBlank())
                    .collect(Collectors.joining("\n"));
        } catch (Exception e) {
            return json;
        }
    }

    // ---- status badges ---------------------------------------------------------------------------------

    /** The badge of a check status: same classes and labels as the reference tool. */
    public static Status status(CheckStatus status) {
        return switch (status) {
            case VERIFIED -> new Status("badge-verified", "Verified", "check");
            case DISCREPANCY -> new Status("badge-discrepancy", "Discrepancy", "cross");
            case UNABLE_TO_VERIFY -> new Status("badge-unverified", "Unable to Verify", "info");
            case CLOSED -> new Status("badge-closed", "Closed", "dash");
            case PENDING -> new Status("badge-pending", "Pending", "clock");
            case IN_PROGRESS -> new Status("badge-inprogress", "In Progress", "refresh");
        };
    }

    /**
     * How serious a status is, for the one card that stands for a group of checks: the most serious
     * one wins, so a group is never shown as Verified while one of its checks is not.
     */
    public static int severity(CheckStatus status) {
        return switch (status) {
            case DISCREPANCY -> 5;
            case UNABLE_TO_VERIFY -> 4;
            case IN_PROGRESS -> 3;
            case PENDING -> 2;
            case CLOSED -> 1;
            case VERIFIED -> 0;
        };
    }

    // ---- page-1 pill -----------------------------------------------------------------------------------

    /** The pill colours and icon of a preset (COMPLETED, DISCREPANCY, UNABLE, CLOSED); unknown presets look like COMPLETED. */
    public static Pill pill(String preset, String title, String subtitle) {
        return switch (preset == null ? "" : preset) {
            case "DISCREPANCY" -> new Pill("#fee2e2", "#fca5a5", "#dc2626", "#dc2626",
                    "<line x1=\"18\" y1=\"6\" x2=\"6\" y2=\"18\"/><line x1=\"6\" y1=\"6\" x2=\"18\" y2=\"18\"/>", title, subtitle);
            case "UNABLE" -> new Pill("#fef3c7", "#fbbf24", "#b45309", "#b45309",
                    "<circle cx=\"12\" cy=\"12\" r=\"10\"/><line x1=\"12\" y1=\"8\" x2=\"12\" y2=\"12\"/><line x1=\"12\" y1=\"16\" x2=\"12.01\" y2=\"16\"/>", title, subtitle);
            case "CLOSED" -> new Pill("#f3f4f6", "#d1d5db", "#374151", "#6b7280",
                    "<line x1=\"5\" y1=\"12\" x2=\"19\" y2=\"12\"/>", title, subtitle);
            default -> new Pill("#d1fae5", "#6ee7b7", "#059669", "#059669",
                    "<polyline points=\"20 6 9 17 4 12\"/>", title, subtitle);
        };
    }
}
