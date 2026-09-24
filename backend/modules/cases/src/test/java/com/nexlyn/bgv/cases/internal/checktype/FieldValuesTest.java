package com.nexlyn.bgv.cases.internal.checktype;

import com.nexlyn.bgv.cases.internal.checktype.CheckTypeDefinition.FieldDefinition;
import com.nexlyn.bgv.cases.internal.checktype.CheckTypeDefinition.ItemField;
import com.nexlyn.bgv.common.validation.Verhoeff;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FieldValuesTest {

    private static FieldDefinition field(FieldType type) {
        return new FieldDefinition("f", "F", type, false, false, null, false, List.of(), List.of());
    }

    private static String ok(FieldType type, String raw) {
        return FieldValues.normalize(field(type), raw);
    }

    private static void bad(FieldType type, String raw, String messagePart) {
        assertThatThrownBy(() -> FieldValues.normalize(field(type), raw))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining(messagePart);
    }

    @Test
    void blankAlwaysMeansNoValueBecauseDraftsAreAllowed() {
        for (FieldType type : FieldType.values()) {
            assertThat(ok(type, null)).as(type.name()).isNull();
            assertThat(ok(type, "   ")).as(type.name()).isNull();
        }
    }

    @Test
    void textIsTrimmedAndLimited() {
        assertThat(ok(FieldType.TEXT, "  Acme Ltd ")).isEqualTo("Acme Ltd");
        bad(FieldType.TEXT, "x".repeat(501), "too long");
        assertThat(ok(FieldType.TEXTAREA, "x".repeat(5000))).hasSize(5000);
        bad(FieldType.TEXTAREA, "x".repeat(5001), "too long");
    }

    @Test
    void datesMustBeIsoAndSensible() {
        assertThat(ok(FieldType.DATE, "2026-05-17")).isEqualTo("2026-05-17");
        bad(FieldType.DATE, "17/05/2026", "yyyy-mm-dd");
        bad(FieldType.DATE, "2026-02-30", "yyyy-mm-dd");
        bad(FieldType.DATE, "1850-01-01", "1900 and 2100");
        bad(FieldType.DATE, "2200-01-01", "1900 and 2100");
    }

    @Test
    void numbersPinsPhonesAndBooleans() {
        assertThat(ok(FieldType.NUMBER, "742")).isEqualTo("742");
        assertThat(ok(FieldType.NUMBER, "-3.5")).isEqualTo("-3.5");
        bad(FieldType.NUMBER, "12a", "number");
        assertThat(ok(FieldType.PIN, "560001")).isEqualTo("560001");
        bad(FieldType.PIN, "060001", "PIN");
        assertThat(ok(FieldType.PHONE, "98765 43210")).isEqualTo("+919876543210");
        bad(FieldType.PHONE, "12345", "mobile");
        assertThat(ok(FieldType.BOOLEAN, "Yes")).isEqualTo("true");
        assertThat(ok(FieldType.BOOLEAN, "FALSE")).isEqualTo("false");
        bad(FieldType.BOOLEAN, "maybe", "yes or no");
    }

    @Test
    void aadhaarNeedsTwelveDigitsAValidFirstDigitAndTheVerhoeffChecksum() {
        String body = "23456789012";
        String valid = body + Verhoeff.checkDigit(body);
        assertThat(ok(FieldType.AADHAAR, valid)).isEqualTo(valid);
        assertThat(ok(FieldType.AADHAAR, valid.substring(0, 4) + " " + valid.substring(4, 8) + "-" + valid.substring(8))).isEqualTo(valid);

        String wrongCheck = body + (char) ('0' + ((Verhoeff.checkDigit(body) - '0' + 1) % 10));
        bad(FieldType.AADHAAR, wrongCheck, "not a valid Aadhaar");
        bad(FieldType.AADHAAR, "0" + valid.substring(1), "not a valid Aadhaar");
        bad(FieldType.AADHAAR, valid.substring(0, 11), "not a valid Aadhaar");
        bad(FieldType.AADHAAR, "abcdefghijkl", "not a valid Aadhaar");
    }

    @Test
    void panIsUppercasedAndMustHaveTheRightShape() {
        assertThat(ok(FieldType.PAN, " abcde1234f ")).isEqualTo("ABCDE1234F");
        bad(FieldType.PAN, "ABCD12345F", "PAN");
        bad(FieldType.PAN, "ABCDE12345", "PAN");
        bad(FieldType.PAN, "ABCDE1234", "PAN");
    }

    @Test
    void uanIsTwelveDigits() {
        assertThat(ok(FieldType.UAN, "1012 3456 7890")).isEqualTo("101234567890");
        bad(FieldType.UAN, "12345678901", "12-digit");
    }

    @Test
    void selectsAcceptOnlyTheListedChoices() {
        FieldDefinition select = new FieldDefinition("s", "S", FieldType.SELECT, false, false, null, false, List.of("Field", "Postal"), List.of());
        assertThat(FieldValues.normalize(select, "Postal")).isEqualTo("Postal");
        assertThatThrownBy(() -> FieldValues.normalize(select, "Courier")).hasMessageContaining("listed choices");
        assertThatThrownBy(() -> FieldValues.normalize(select, "postal")).as("case matters").hasMessageContaining("listed choices");
    }

    private static final FieldDefinition GAPS = new FieldDefinition("gaps", "Gaps", FieldType.REPEATABLE, false, false, null, false, List.of(),
            List.of(new ItemField("from", "From", FieldType.DATE), new ItemField("to", "To", FieldType.DATE), new ItemField("reason", "Reason", FieldType.TEXT)));

    @Test
    void repeatableRowsAreCheckedCellByCellAndEmptyRowsDropped() {
        String cleaned = FieldValues.normalize(GAPS,
                "[{\"from\":\"2020-01-01\",\"to\":\"2020-06-30\",\"reason\":\" Travel \"},{\"from\":\"\",\"to\":\"\",\"reason\":\"\"}]");
        assertThat(cleaned).isEqualTo("[{\"from\":\"2020-01-01\",\"to\":\"2020-06-30\",\"reason\":\"Travel\"}]");
        assertThat(FieldValues.normalize(GAPS, "[{\"from\":\"\",\"to\":\"\",\"reason\":\"\"}]")).as("nothing left").isNull();
    }

    @Test
    void repeatableRowsRefuseBadCellsUnknownColumnsAndWrongShapes() {
        assertThatThrownBy(() -> FieldValues.normalize(GAPS, "[{\"from\":\"yesterday\"}]")).hasMessageContaining("From").hasMessageContaining("yyyy-mm-dd");
        assertThatThrownBy(() -> FieldValues.normalize(GAPS, "[{\"from\":\"2020-01-01\",\"secret\":\"x\"}]")).hasMessageContaining("unknown column");
        assertThatThrownBy(() -> FieldValues.normalize(GAPS, "{\"from\":\"2020-01-01\"}")).hasMessageContaining("list of rows");
        assertThatThrownBy(() -> FieldValues.normalize(GAPS, "not json")).hasMessageContaining("not a valid list");
        assertThatThrownBy(() -> FieldValues.normalize(GAPS, "[" + "{\"reason\":\"x\"},".repeat(51) + "{\"reason\":\"x\"}]")).hasMessageContaining("too many rows");
    }

    @Test
    void errorMessagesNeverContainTheRejectedValue() {
        assertThatThrownBy(() -> FieldValues.normalize(field(FieldType.AADHAAR), "999911112222"))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("9999"));
        assertThatThrownBy(() -> FieldValues.normalize(field(FieldType.PAN), "SECRETPAN1"))
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("SECRETPAN1"));
    }
}
