package com.nexlyn.bgv.auth.internal.service;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class TotpServiceTest {

    /** RFC 6238 appendix B secret: the ASCII string "12345678901234567890" in Base32. */
    private static final String RFC_SECRET = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    private final TotpService totp = new TotpService();

    @Test
    void matchesTheRfc6238TestVectors() {
        // The RFC lists 8-digit codes; the 6-digit code is the last six digits of each.
        assertThat(totp.codeForStep(RFC_SECRET, totp.stepAt(Instant.ofEpochSecond(59)))).isEqualTo("287082");
        assertThat(totp.codeForStep(RFC_SECRET, totp.stepAt(Instant.ofEpochSecond(1111111109L)))).isEqualTo("081804");
        assertThat(totp.codeForStep(RFC_SECRET, totp.stepAt(Instant.ofEpochSecond(1111111111L)))).isEqualTo("050471");
        assertThat(totp.codeForStep(RFC_SECRET, totp.stepAt(Instant.ofEpochSecond(1234567890L)))).isEqualTo("005924");
        assertThat(totp.codeForStep(RFC_SECRET, totp.stepAt(Instant.ofEpochSecond(2000000000L)))).isEqualTo("279037");
        assertThat(totp.codeForStep(RFC_SECRET, totp.stepAt(Instant.ofEpochSecond(20000000000L)))).isEqualTo("353130");
    }

    @Test
    void acceptsTheCurrentAndNeighbouringStepsButNoOthers() {
        Instant now = Instant.ofEpochSecond(1111111111L);
        long step = totp.stepAt(now);

        assertThat(totp.findMatchingStep(RFC_SECRET, totp.codeForStep(RFC_SECRET, step), now)).hasValue(step);
        assertThat(totp.findMatchingStep(RFC_SECRET, totp.codeForStep(RFC_SECRET, step - 1), now)).hasValue(step - 1);
        assertThat(totp.findMatchingStep(RFC_SECRET, totp.codeForStep(RFC_SECRET, step + 1), now)).hasValue(step + 1);
        assertThat(totp.findMatchingStep(RFC_SECRET, totp.codeForStep(RFC_SECRET, step - 2), now)).isEmpty();
        assertThat(totp.findMatchingStep(RFC_SECRET, totp.codeForStep(RFC_SECRET, step + 2), now)).isEmpty();
    }

    @Test
    void rejectsMalformedCodes() {
        Instant now = Instant.ofEpochSecond(1111111111L);
        assertThat(totp.findMatchingStep(RFC_SECRET, null, now)).isEmpty();
        assertThat(totp.findMatchingStep(RFC_SECRET, "", now)).isEmpty();
        assertThat(totp.findMatchingStep(RFC_SECRET, "12345", now)).isEmpty();
        assertThat(totp.findMatchingStep(RFC_SECRET, "1234567", now)).isEmpty();
        assertThat(totp.findMatchingStep(RFC_SECRET, "abcdef", now)).isEmpty();
        assertThat(totp.findMatchingStep(RFC_SECRET, "050 471", now)).isEmpty();
    }

    @Test
    void generatesRandomBase32SecretsOfTheRightSize() {
        String a = totp.generateSecret();
        String b = totp.generateSecret();
        assertThat(a).isNotEqualTo(b).matches("[A-Z2-7]{32}"); // 160 bits, unpadded
    }

    @Test
    void generatedSecretsRoundTripThroughCodeGeneration() {
        String secret = totp.generateSecret();
        Instant now = Instant.now();
        String code = totp.codeForStep(secret, totp.stepAt(now));
        assertThat(code).matches("\\d{6}");
        assertThat(totp.findMatchingStep(secret, code, now)).hasValue(totp.stepAt(now));
    }

    @Test
    void buildsAnOtpauthLinkAuthenticatorAppsUnderstand() {
        assertThat(totp.otpauthUri("Nexlyn BGV", "owner@example.com", "ABCDEFGH"))
                .isEqualTo("otpauth://totp/Nexlyn%20BGV:owner%40example.com"
                        + "?secret=ABCDEFGH&issuer=Nexlyn%20BGV&algorithm=SHA1&digits=6&period=30");
    }
}
