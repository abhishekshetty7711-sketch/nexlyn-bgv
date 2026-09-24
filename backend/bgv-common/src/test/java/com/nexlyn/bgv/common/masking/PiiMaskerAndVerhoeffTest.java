package com.nexlyn.bgv.common.masking;

import com.nexlyn.bgv.common.validation.Verhoeff;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PiiMaskerAndVerhoeffTest {

    @Test
    void masksAadhaarLikeTheReport() {
        assertThat(PiiMasker.maskAadhaar("234567890124")).isEqualTo("XXXX XXXX 0124");
        assertThat(PiiMasker.maskAadhaar("2345 6789 0124")).isEqualTo("XXXX XXXX 0124");
        assertThat(PiiMasker.maskAadhaar("2345-6789-0124")).isEqualTo("XXXX XXXX 0124");
    }

    @Test
    void masksPanKeepingTheFirstTwoAndLastThree() {
        assertThat(PiiMasker.maskPan("ABCDE1234F")).isEqualTo("ABXXXXX34F");
    }

    @Test
    void neverRevealsMoreThanHalfOfAShortOrUnusualValue() {
        assertThat(PiiMasker.maskGeneric("123456789012")).isEqualTo("XXXXXXXX9012");
        assertThat(PiiMasker.maskGeneric("1234")).isEqualTo("XX34");
        assertThat(PiiMasker.maskGeneric("12")).isEqualTo("X2");
        assertThat(PiiMasker.maskGeneric("1")).isEqualTo("X");
        assertThat(PiiMasker.maskGeneric("")).isEmpty();
        assertThat(PiiMasker.maskGeneric(null)).isEmpty();
        assertThat(PiiMasker.maskAadhaar("12345")).as("not an Aadhaar shape").isEqualTo("XXX45");
        assertThat(PiiMasker.maskPan("SHORT")).as("not a PAN shape").isEqualTo("XXXRT");
    }

    @Test
    void keepsTheLastFour() {
        assertThat(PiiMasker.last4("234567890124")).isEqualTo("0124");
        assertThat(PiiMasker.last4("12")).isEqualTo("12");
        assertThat(PiiMasker.last4(null)).isNull();
    }

    @Test
    void verhoeffAcceptsNumbersWithACorrectCheckDigit() {
        // The classic worked example from the algorithm's description.
        assertThat(Verhoeff.isValid("2363")).isTrue();
        String body = "23456789012";
        String full = body + Verhoeff.checkDigit(body);
        assertThat(Verhoeff.isValid(full)).isTrue();
    }

    @Test
    void verhoeffCatchesTypingMistakes() {
        String body = "23456789012";
        String valid = body + Verhoeff.checkDigit(body);
        // every single wrong digit
        for (int i = 0; i < valid.length(); i++) {
            char original = valid.charAt(i);
            for (char other = '0'; other <= '9'; other++) {
                if (other != original) {
                    String wrong = valid.substring(0, i) + other + valid.substring(i + 1);
                    assertThat(Verhoeff.isValid(wrong)).as(wrong).isFalse();
                }
            }
        }
        // swapping two different neighbouring digits
        for (int i = 0; i < valid.length() - 1; i++) {
            if (valid.charAt(i) != valid.charAt(i + 1)) {
                String swapped = valid.substring(0, i) + valid.charAt(i + 1) + valid.charAt(i) + valid.substring(i + 2);
                assertThat(Verhoeff.isValid(swapped)).as(swapped).isFalse();
            }
        }
    }

    @Test
    void verhoeffRejectsNonNumbers() {
        assertThat(Verhoeff.isValid("")).isFalse();
        assertThat(Verhoeff.isValid(null)).isFalse();
        assertThat(Verhoeff.isValid("23a3")).isFalse();
        assertThat(Verhoeff.isValid("23 63")).isFalse();
    }
}
