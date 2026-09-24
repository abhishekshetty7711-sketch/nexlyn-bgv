package com.nexlyn.bgv.cases.internal.checktype;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexlyn.bgv.cases.internal.checktype.CheckTypeDefinition.FieldDefinition;
import com.nexlyn.bgv.cases.internal.checktype.CheckTypeDefinition.ItemField;
import com.nexlyn.bgv.common.validation.IndianPhone;
import com.nexlyn.bgv.common.validation.Verhoeff;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Checks and tidies one field value according to its type (CLAUDE.md {@literal §8}) and returns the
 * form that is stored: trimmed text, ISO dates, digits-only Aadhaar and UAN, upper-case PAN,
 * {@code +91XXXXXXXXXX} phones, {@code true}/{@code false}. Blank means "no value" and is always
 * accepted (a draft), so it returns null. Problems are reported as {@link IllegalArgumentException}
 * with a message safe to show (it never contains the value).
 */
public final class FieldValues {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern PIN = Pattern.compile("^[1-9][0-9]{5}$");
    private static final Pattern PAN = Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");
    private static final Pattern UAN = Pattern.compile("^[0-9]{12}$");
    private static final Pattern AADHAAR = Pattern.compile("^[2-9][0-9]{11}$");
    private static final Pattern NUMBER = Pattern.compile("^-?[0-9]{1,12}(\\.[0-9]{1,6})?$");
    private static final LocalDate EARLIEST = LocalDate.of(1900, 1, 1);
    private static final LocalDate LATEST = LocalDate.of(2100, 12, 31);
    static final int MAX_TEXT = 500;
    static final int MAX_TEXTAREA = 5000;
    static final int MAX_ROWS = 50;
    private static final Set<String> TRUE_WORDS = Set.of("true", "yes");
    private static final Set<String> FALSE_WORDS = Set.of("false", "no");

    private FieldValues() {
    }

    /** The normalised value, or null if {@code raw} is blank. */
    public static String normalize(FieldDefinition field, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        if (field.type() == FieldType.REPEATABLE) {
            return normalizeRows(field, raw);
        }
        return normalizeScalar(field.type(), raw.trim(), field.options());
    }

    static String normalizeScalar(FieldType type, String value, List<String> options) {
        return switch (type) {
            case TEXT -> limited(value, MAX_TEXT);
            case TEXTAREA -> limited(value, MAX_TEXTAREA);
            case DATE -> date(value);
            case NUMBER -> {
                if (!NUMBER.matcher(value).matches()) {
                    throw new IllegalArgumentException("must be a number");
                }
                yield value;
            }
            case PIN -> {
                if (!PIN.matcher(value).matches()) {
                    throw new IllegalArgumentException("must be a 6-digit PIN code");
                }
                yield value;
            }
            case PHONE -> {
                String phone = IndianPhone.normalize(value);
                if (phone == null) {
                    throw new IllegalArgumentException("must be a valid Indian mobile number");
                }
                yield phone;
            }
            case AADHAAR -> {
                String digits = value.replaceAll("[\\s-]", "");
                if (!AADHAAR.matcher(digits).matches() || !Verhoeff.isValid(digits)) {
                    throw new IllegalArgumentException("is not a valid Aadhaar number");
                }
                yield digits;
            }
            case PAN -> {
                String pan = value.replaceAll("\\s", "").toUpperCase(Locale.ROOT);
                if (!PAN.matcher(pan).matches()) {
                    throw new IllegalArgumentException("is not a valid PAN (five letters, four digits, one letter)");
                }
                yield pan;
            }
            case UAN -> {
                String digits = value.replaceAll("[\\s-]", "");
                if (!UAN.matcher(digits).matches()) {
                    throw new IllegalArgumentException("must be a 12-digit UAN");
                }
                yield digits;
            }
            case BOOLEAN -> {
                String word = value.toLowerCase(Locale.ROOT);
                if (TRUE_WORDS.contains(word)) {
                    yield "true";
                }
                if (FALSE_WORDS.contains(word)) {
                    yield "false";
                }
                throw new IllegalArgumentException("must be yes or no");
            }
            case SELECT -> {
                if (!options.contains(value)) {
                    throw new IllegalArgumentException("must be one of the listed choices");
                }
                yield value;
            }
            case REPEATABLE -> throw new IllegalArgumentException("a repeatable field cannot be nested");
        };
    }

    /** A repeatable field is stored as a JSON array of rows; each cell is checked by its column's type. */
    private static String normalizeRows(FieldDefinition field, String raw) {
        JsonNode parsed;
        try {
            parsed = JSON.readTree(raw);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("is not a valid list of rows");
        }
        if (parsed == null || !parsed.isArray()) {
            throw new IllegalArgumentException("must be a list of rows");
        }
        if (parsed.size() > MAX_ROWS) {
            throw new IllegalArgumentException("has too many rows (at most " + MAX_ROWS + ")");
        }
        ArrayNode out = JSON.createArrayNode();
        for (JsonNode row : parsed) {
            if (!row.isObject()) {
                throw new IllegalArgumentException("must be a list of rows");
            }
            ObjectNode cleaned = JSON.createObjectNode();
            boolean anyValue = false;
            for (ItemField column : field.itemFields()) {
                JsonNode cell = row.get(column.key());
                String text = cell == null || cell.isNull() ? "" : cell.asText("");
                String normalized = text.isBlank() ? null : normalizeCell(column, text.trim());
                cleaned.put(column.key(), normalized == null ? "" : normalized);
                anyValue |= normalized != null;
            }
            row.fieldNames().forEachRemaining(name -> {
                if (field.itemFields().stream().noneMatch(c -> c.key().equals(name))) {
                    throw new IllegalArgumentException("has an unknown column");
                }
            });
            if (anyValue) {
                out.add(cleaned); // a row with nothing in it is dropped
            }
        }
        return out.isEmpty() ? null : out.toString();
    }

    private static String normalizeCell(ItemField column, String value) {
        try {
            return normalizeScalar(column.type(), value, List.of());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("row value '" + column.label() + "' " + e.getMessage());
        }
    }

    private static String limited(String value, int max) {
        if (value.length() > max) {
            throw new IllegalArgumentException("is too long (at most " + max + " characters)");
        }
        return value;
    }

    private static String date(String value) {
        try {
            LocalDate parsed = LocalDate.parse(value);
            if (parsed.isBefore(EARLIEST) || parsed.isAfter(LATEST)) {
                throw new IllegalArgumentException("must be a date between 1900 and 2100");
            }
            return parsed.toString();
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("must be a date (yyyy-mm-dd)");
        }
    }
}
