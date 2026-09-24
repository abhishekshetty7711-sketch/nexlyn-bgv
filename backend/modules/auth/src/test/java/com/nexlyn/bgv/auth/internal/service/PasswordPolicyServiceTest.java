package com.nexlyn.bgv.auth.internal.service;

import com.nexlyn.bgv.auth.internal.service.PasswordPolicyService.Problem;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyServiceTest {

    private final PasswordPolicyService policy = new PasswordPolicyService();

    @Test
    void acceptsAStrongPasswordAndAStrongPassphrase() {
        assertThat(policy.check("Tr1cky-Orange-Kettle", "a@b.co")).isEmpty();
        assertThat(policy.check("purple elephant drinks 7 teas", "a@b.co")).isEmpty();
    }

    @Test
    void rejectsShortPasswords() {
        assertThat(policy.check("Ab1!xyz", "a@b.co")).contains(Problem.TOO_SHORT);
        assertThat(policy.check(null, "a@b.co")).contains(Problem.TOO_SHORT);
    }

    @Test
    void minimumLengthIsTwelveAndCannotBeLoweredByAccident() {
        assertThat(PasswordPolicyService.MIN_LENGTH).isGreaterThanOrEqualTo(12);
        // 11 characters (strong mix) is rejected, 12 is accepted.
        assertThat(policy.check("Tr1cky-Ora1", "a@b.co")).containsExactly(Problem.TOO_SHORT);
        assertThat(policy.check("Tr1cky-Ora12", "a@b.co")).isEmpty();
    }

    @Test
    void needsAtLeastThreeCharacterClasses() {
        assertThat(policy.check("alllowercaseletters", "a@b.co")).contains(Problem.NOT_ENOUGH_CHARACTER_TYPES);
        assertThat(policy.check("lowerandUPPERonly", "a@b.co")).contains(Problem.NOT_ENOUGH_CHARACTER_TYPES);
        assertThat(policy.check("digits123456789012", "a@b.co")).contains(Problem.NOT_ENOUGH_CHARACTER_TYPES);
        assertThat(policy.check("123456789012345", "a@b.co")).contains(Problem.NOT_ENOUGH_CHARACTER_TYPES);
        assertThat(policy.check("lower-and-symbols-only", "a@b.co")).contains(Problem.NOT_ENOUGH_CHARACTER_TYPES);
        assertThat(policy.check("lower-UPPER-symbols", "a@b.co")).doesNotContain(Problem.NOT_ENOUGH_CHARACTER_TYPES);
    }

    @Test
    void rejectsThePasswordBeingTheEmailAddress() {
        assertThat(policy.check("Owner.Person@Example.com", " owner.person@example.com "))
                .contains(Problem.SAME_AS_EMAIL);
    }

    @Test
    void rejectsWellKnownPasswordsEvenWhenDecorated() {
        assertThat(policy.check("Password@2024!", "a@b.co")).contains(Problem.COMMON_PASSWORD);
        assertThat(policy.check("1qaz2wsx3edc", "a@b.co")).contains(Problem.COMMON_PASSWORD);
        assertThat(policy.check("NEXLYN-2026-Admin", "a@b.co")).doesNotContain(Problem.COMMON_PASSWORD);
    }

    @Test
    void reportsEveryBrokenRuleAtOnce() {
        assertThat(policy.check("password", "a@b.co")).containsExactlyInAnyOrder(
                Problem.TOO_SHORT, Problem.NOT_ENOUGH_CHARACTER_TYPES, Problem.COMMON_PASSWORD);
    }
}
