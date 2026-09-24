package com.nexlyn.bgv.auth.internal.security;

import com.nexlyn.bgv.auth.internal.config.AuthProperties;
import com.nexlyn.bgv.auth.internal.security.JwtService.ChallengePurpose;
import com.nexlyn.bgv.auth.internal.security.JwtService.InvalidTokenException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final UUID ADMIN = UUID.randomUUID();
    private static final UUID SESSION = UUID.randomUUID();

    private static KeyPair newPair() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }

    private static String pem(String label, byte[] der) {
        return "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der)
                + "\n-----END " + label + "-----";
    }

    private static AuthProperties props(String privateKey, String publicKey, String keyId,
                                        List<AuthProperties.PreviousKey> previous) {
        return new AuthProperties(null, null, null, new AuthProperties.Jwt(privateKey, publicKey, keyId,
                "nexlyn-bgv", Duration.ofMinutes(15), Duration.ofMinutes(5), previous), null, null, null);
    }

    private static JwtService service(JwtKeyProvider keys, AuthProperties props, Instant at) {
        return new JwtService(keys, props, Clock.fixed(at, ZoneOffset.UTC));
    }

    /** A service with a temporary key, as used when no key is configured. */
    private static JwtService ephemeral(Instant at) {
        AuthProperties props = props(null, null, null, List.of());
        return service(new JwtKeyProvider(props, new MockEnvironment()), props, at);
    }

    @Test
    void accessTokenRoundTripsItsClaims() {
        JwtService jwt = ephemeral(NOW);
        String token = jwt.issueAccessToken(ADMIN, "owner@example.com", List.of("SUPER_ADMIN"),
                List.of("CASE_CREATE", "AUDIT_READ"), SESSION);

        JwtService.AccessClaims claims = jwt.parseAccessToken(token);
        assertThat(claims.adminId()).isEqualTo(ADMIN);
        assertThat(claims.email()).isEqualTo("owner@example.com");
        assertThat(claims.roles()).containsExactly("SUPER_ADMIN");
        assertThat(claims.permissions()).containsExactlyInAnyOrder("CASE_CREATE", "AUDIT_READ");
        assertThat(claims.sessionId()).isEqualTo(SESSION);
        assertThat(claims.tokenId()).isNotBlank();
        assertThat(claims.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void challengeTokenCarriesItsPurposeAndExpiresAfterFiveMinutes() {
        JwtService jwt = ephemeral(NOW);
        String token = jwt.issueChallenge(ADMIN, ChallengePurpose.SETUP);
        assertThat(jwt.parseChallenge(token).purpose()).isEqualTo(ChallengePurpose.SETUP);
        assertThat(jwt.parseChallenge(token).adminId()).isEqualTo(ADMIN);
    }

    @Test
    void anAccessTokenCannotBeUsedAsAChallengeAndViceVersa() {
        JwtService jwt = ephemeral(NOW);
        String access = jwt.issueAccessToken(ADMIN, "a@b.co", List.of(), List.of(), SESSION);
        String challenge = jwt.issueChallenge(ADMIN, ChallengePurpose.VERIFY);
        assertThatThrownBy(() -> jwt.parseChallenge(access)).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> jwt.parseAccessToken(challenge)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void expiredTokensAreRejected() throws Exception {
        AuthProperties props = props(null, null, null, List.of());
        JwtKeyProvider keys = new JwtKeyProvider(props, new MockEnvironment());
        String access = service(keys, props, NOW).issueAccessToken(ADMIN, "a@b.co", List.of(), List.of(), SESSION);
        String challenge = service(keys, props, NOW).issueChallenge(ADMIN, ChallengePurpose.VERIFY);

        assertThat(service(keys, props, NOW.plus(Duration.ofMinutes(14))).parseAccessToken(access)).isNotNull();
        assertThatThrownBy(() -> service(keys, props, NOW.plus(Duration.ofMinutes(15))).parseAccessToken(access))
                .isInstanceOf(InvalidTokenException.class).hasMessageContaining("expired");
        assertThatThrownBy(() -> service(keys, props, NOW.plus(Duration.ofMinutes(5))).parseChallenge(challenge))
                .isInstanceOf(InvalidTokenException.class).hasMessageContaining("expired");
    }

    @Test
    void aTamperedPayloadIsRejected() {
        JwtService jwt = ephemeral(NOW);
        String token = jwt.issueAccessToken(ADMIN, "a@b.co", List.of("ANALYST"), List.of(), SESSION);
        String[] parts = token.split("\\.");
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]));
        String forged = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.replace("ANALYST", "SUPER_ADMIN").getBytes());
        assertThatThrownBy(() -> jwt.parseAccessToken(parts[0] + "." + forged + "." + parts[2]))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void aTokenSignedByAnotherKeyIsRejected() {
        String token = ephemeral(NOW).issueAccessToken(ADMIN, "a@b.co", List.of(), List.of(), SESSION);
        assertThatThrownBy(() -> ephemeral(NOW).parseAccessToken(token)).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void unsignedAndAlgorithmSwappedTokensAreRejected() throws Exception {
        AuthProperties props = props(null, null, null, List.of());
        JwtKeyProvider keys = new JwtKeyProvider(props, new MockEnvironment());
        JwtService jwt = service(keys, props, NOW);
        JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("nexlyn-bgv").subject(ADMIN.toString())
                .expirationTime(Date.from(NOW.plusSeconds(600))).claim("typ", "access")
                .claim("sid", SESSION.toString()).build();

        // "alg": "none"
        assertThatThrownBy(() -> jwt.parseAccessToken(new PlainJWT(claims).serialize()))
                .isInstanceOf(InvalidTokenException.class);

        // HS256 signed with the public key as the shared secret (the classic algorithm-confusion attack)
        byte[] secret = keys.verificationKey(keys.signingKeyId()).getEncoded();
        SignedJWT hs = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.HS256).keyID(keys.signingKeyId()).build(), claims);
        hs.sign(new MACSigner(secret));
        assertThatThrownBy(() -> jwt.parseAccessToken(hs.serialize()))
                .isInstanceOf(InvalidTokenException.class).hasMessageContaining("algorithm");
    }

    @Test
    void garbageAndMissingTokensAreRejected() {
        JwtService jwt = ephemeral(NOW);
        assertThatThrownBy(() -> jwt.parseAccessToken(null)).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> jwt.parseAccessToken("")).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> jwt.parseAccessToken("not.a.jwt")).isInstanceOf(InvalidTokenException.class);
        assertThatThrownBy(() -> jwt.parseChallenge("garbage")).isInstanceOf(InvalidTokenException.class);
    }

    @Test
    void loadsAConfiguredKeyFromPemWithRealOrEscapedNewlines() throws Exception {
        KeyPair pair = newPair();
        String privatePem = pem("PRIVATE KEY", pair.getPrivate().getEncoded());
        String publicPem = pem("PUBLIC KEY", pair.getPublic().getEncoded());
        String escaped = privatePem.replace("\n", "\\n"); // how it looks inside an .env file

        AuthProperties real = props(privatePem, publicPem, "k1", List.of());
        AuthProperties env = props(escaped, null, "k1", List.of());
        JwtService a = service(new JwtKeyProvider(real, new MockEnvironment()), real, NOW);
        JwtService b = service(new JwtKeyProvider(env, new MockEnvironment()), env, NOW);

        // Same key from either form, so a token from one verifies in the other.
        assertThat(b.parseAccessToken(a.issueAccessToken(ADMIN, "a@b.co", List.of(), List.of(), SESSION))).isNotNull();
    }

    @Test
    void refusesAPublicKeyThatDoesNotMatchThePrivateKey() throws Exception {
        KeyPair pair = newPair();
        KeyPair other = newPair();
        AuthProperties props = props(pem("PRIVATE KEY", pair.getPrivate().getEncoded()),
                pem("PUBLIC KEY", other.getPublic().getEncoded()), "k1", List.of());
        assertThatThrownBy(() -> new JwtKeyProvider(props, new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("does not match");
    }

    @Test
    void refusesToStartInProdWithoutAKey() {
        AuthProperties props = props(null, null, null, List.of());
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertThatThrownBy(() -> new JwtKeyProvider(props, prod))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("JWT_PRIVATE_KEY");
    }

    @Test
    void keyRotationKeepsOldTokensValidUntilTheOldKeyIsRemoved() throws Exception {
        KeyPair oldPair = newPair();
        KeyPair newPair = newPair();
        AuthProperties oldProps = props(pem("PRIVATE KEY", oldPair.getPrivate().getEncoded()), null, "2026-01", List.of());
        String oldToken = service(new JwtKeyProvider(oldProps, new MockEnvironment()), oldProps, NOW)
                .issueAccessToken(ADMIN, "a@b.co", List.of(), List.of(), SESSION);

        AuthProperties rotated = props(pem("PRIVATE KEY", newPair.getPrivate().getEncoded()), null, "2026-02",
                List.of(new AuthProperties.PreviousKey("2026-01", pem("PUBLIC KEY", oldPair.getPublic().getEncoded()))));
        JwtService afterRotation = service(new JwtKeyProvider(rotated, new MockEnvironment()), rotated, NOW);
        assertThat(afterRotation.parseAccessToken(oldToken)).as("old key still trusted").isNotNull();

        String newToken = afterRotation.issueAccessToken(ADMIN, "a@b.co", List.of(), List.of(), SESSION);
        assertThat(SignedJWTHeader.kid(newToken)).isEqualTo("2026-02");

        AuthProperties oldKeyRemoved = props(pem("PRIVATE KEY", newPair.getPrivate().getEncoded()), null, "2026-02", List.of());
        JwtService cleaned = service(new JwtKeyProvider(oldKeyRemoved, new MockEnvironment()), oldKeyRemoved, NOW);
        assertThatThrownBy(() -> cleaned.parseAccessToken(oldToken)).isInstanceOf(InvalidTokenException.class);
    }

    /** Reads the {@code kid} header of a token without verifying it. */
    private static final class SignedJWTHeader {
        static String kid(String token) {
            try {
                return SignedJWT.parse(token).getHeader().getKeyID();
            } catch (java.text.ParseException e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
