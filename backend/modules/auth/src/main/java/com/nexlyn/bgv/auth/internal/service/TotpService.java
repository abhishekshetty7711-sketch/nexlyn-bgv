package com.nexlyn.bgv.auth.internal.service;

import org.apache.commons.codec.binary.Base32;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.OptionalLong;

/**
 * TOTP (RFC 6238): HMAC-SHA1, 6 digits, 30-second steps, accepting the previous, current and next
 * step to allow for clock drift. Compatible with Google Authenticator, Microsoft Authenticator,
 * Authy and 1Password. Only the maths lives here; replay protection is in {@code TwoFactorService}.
 */
@Service
public class TotpService {

    static final int STEP_SECONDS = 30;
    static final int DIGITS = 6;
    private static final int ALLOWED_DRIFT_STEPS = 1;
    private static final int SECRET_BYTES = 20; // 160 bits, the RFC 4226 recommendation

    private final SecureRandom random = new SecureRandom();

    /** A new random secret as unpadded Base32, the format authenticator apps expect. */
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return new Base32().encodeToString(bytes).replace("=", "");
    }

    /** The {@code otpauth://} link (and QR content) that enrols the secret in an authenticator app. */
    public String otpauthUri(String issuer, String account, String secretBase32) {
        String label = encode(issuer) + ":" + encode(account);
        return "otpauth://totp/" + label + "?secret=" + secretBase32 + "&issuer=" + encode(issuer)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    public long stepAt(Instant time) {
        return Math.floorDiv(time.getEpochSecond(), STEP_SECONDS);
    }

    /** The 6-digit code for a time step. */
    public String codeForStep(String secretBase32, long step) {
        byte[] key = new Base32().decode(secretBase32);
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                    | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%0" + DIGITS + "d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA1 is unavailable", e);
        }
    }

    /**
     * The time step whose code equals {@code code}, looking one step either side of {@code now}.
     * Comparison is constant-time. Empty when the code is wrong.
     */
    public OptionalLong findMatchingStep(String secretBase32, String code, Instant now) {
        if (code == null || !code.matches("\\d{" + DIGITS + "}")) {
            return OptionalLong.empty();
        }
        long current = stepAt(now);
        byte[] given = code.getBytes(StandardCharsets.US_ASCII);
        OptionalLong match = OptionalLong.empty();
        // Check every step (no early exit) so timing does not reveal which one matched.
        for (long step = current - ALLOWED_DRIFT_STEPS; step <= current + ALLOWED_DRIFT_STEPS; step++) {
            byte[] expected = codeForStep(secretBase32, step).getBytes(StandardCharsets.US_ASCII);
            if (MessageDigest.isEqual(expected, given)) {
                match = OptionalLong.of(step);
            }
        }
        return match;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
