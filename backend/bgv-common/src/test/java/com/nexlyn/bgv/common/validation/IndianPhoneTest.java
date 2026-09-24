package com.nexlyn.bgv.common.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IndianPhoneTest {

    @Test
    void reducesTheWaysPeopleTypeANumberToOneStoredForm() {
        for (String typed : new String[]{"9876543210", "98765 43210", "+91 98765 43210", "+91-98765-43210",
                "09876543210", "919876543210", "0091 98765 43210", "(+91) 98765.43210"}) {
            assertThat(IndianPhone.normalize(typed)).as(typed).isEqualTo("+919876543210");
        }
    }

    @Test
    void rejectsNumbersThatCannotBeIndianMobiles() {
        for (String bad : new String[]{"", "   ", "12345", "5876543210", "98765432", "98765432101", "abcdefghij", "+1 415 555 2671", "+91 12345 67890"}) {
            assertThat(IndianPhone.normalize(bad)).as(bad).isNull();
        }
        assertThat(IndianPhone.normalize(null)).isNull();
    }

    @Test
    void formatsAStoredNumberForDisplay() {
        assertThat(IndianPhone.format("+919876543210")).isEqualTo("+91 98765 43210");
        assertThat(IndianPhone.format(null)).isNull();
        assertThat(IndianPhone.format("not a number")).isEqualTo("not a number");
    }

    @Test
    void theValidatorsTreatBlankAsValidSoRequirednessIsDecidedSeparately() {
        var phone = new ValidIndianPhone.Validator();
        assertThat(phone.isValid(null, null)).isTrue();
        assertThat(phone.isValid("  ", null)).isTrue();
        assertThat(phone.isValid("98765 43210", null)).isTrue();
        assertThat(phone.isValid("12345", null)).isFalse();

        var pin = new ValidPinCode.Validator();
        assertThat(pin.isValid(null, null)).isTrue();
        assertThat(pin.isValid("560001", null)).isTrue();
        assertThat(pin.isValid(" 560001 ", null)).isTrue();
        assertThat(pin.isValid("060001", null)).isFalse();
        assertThat(pin.isValid("56001", null)).isFalse();
        assertThat(pin.isValid("56000A", null)).isFalse();
    }
}
