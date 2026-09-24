package com.nexlyn.bgv.auth.internal.security;

import com.nexlyn.bgv.auth.internal.config.AuthProperties;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * Issues and verifies the two JWTs the auth flow uses (RS256, {@code kid} in the header):
 * <ul>
 *   <li><b>access</b> tokens (15 min): {@code sub, email, roles, perms, sid, jti}</li>
 *   <li><b>challenge</b> tokens (5 min): proof that the password step passed, for 2FA setup/verify</li>
 * </ul>
 * A token of one type is never accepted as the other. Only RS256 is accepted, and only with a
 * trusted {@code kid}, which rules out algorithm-confusion and unsigned tokens.
 */
@Service
public class JwtService {

    public static final String TYPE_ACCESS = "access";
    public static final String TYPE_CHALLENGE = "challenge";

    /** What a challenge token is good for. */
    public enum ChallengePurpose { VERIFY, SETUP }

    /** Verified claims of an access token. */
    public record AccessClaims(UUID adminId, String email, List<String> roles, List<String> permissions,
                               UUID sessionId, String tokenId, Instant expiresAt) {
    }

    /** Verified claims of a challenge token. */
    public record ChallengeClaims(UUID adminId, ChallengePurpose purpose) {
    }

    /** The token is missing, malformed, expired, wrongly signed or of the wrong type. */
    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }

    private final JwtKeyProvider keys;
    private final AuthProperties.Jwt settings;
    private final Clock clock;

    public JwtService(JwtKeyProvider keys, AuthProperties properties, Clock clock) {
        this.keys = keys;
        this.settings = properties.jwt();
        this.clock = clock;
    }

    public Duration accessTtl() {
        return settings.accessTtl();
    }

    public Duration challengeTtl() {
        return settings.challengeTtl();
    }

    public String issueAccessToken(UUID adminId, String email, Collection<String> roles,
                                   Collection<String> permissions, UUID sessionId) {
        JWTClaimsSet.Builder claims = base(adminId, TYPE_ACCESS, settings.accessTtl())
                .claim("email", email)
                .claim("roles", List.copyOf(roles))
                .claim("perms", List.copyOf(permissions))
                .claim("sid", sessionId.toString());
        return sign(claims.build());
    }

    public String issueChallenge(UUID adminId, ChallengePurpose purpose) {
        return sign(base(adminId, TYPE_CHALLENGE, settings.challengeTtl())
                .claim("purpose", purpose.name())
                .build());
    }

    public AccessClaims parseAccessToken(String token) {
        JWTClaimsSet claims = verify(token, TYPE_ACCESS);
        try {
            return new AccessClaims(
                    UUID.fromString(claims.getSubject()),
                    claims.getStringClaim("email"),
                    stringList(claims, "roles"),
                    stringList(claims, "perms"),
                    UUID.fromString(claims.getStringClaim("sid")),
                    claims.getJWTID(),
                    claims.getExpirationTime().toInstant());
        } catch (ParseException | IllegalArgumentException | NullPointerException e) {
            throw new InvalidTokenException("Malformed access token claims");
        }
    }

    public ChallengeClaims parseChallenge(String token) {
        JWTClaimsSet claims = verify(token, TYPE_CHALLENGE);
        try {
            return new ChallengeClaims(UUID.fromString(claims.getSubject()),
                    ChallengePurpose.valueOf(claims.getStringClaim("purpose")));
        } catch (ParseException | IllegalArgumentException | NullPointerException e) {
            throw new InvalidTokenException("Malformed challenge claims");
        }
    }

    private JWTClaimsSet.Builder base(UUID adminId, String type, Duration ttl) {
        Instant now = Instant.now(clock);
        return new JWTClaimsSet.Builder()
                .issuer(settings.issuer())
                .subject(adminId.toString())
                .jwtID(UUID.randomUUID().toString())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ttl)))
                .claim("typ", type);
    }

    private String sign(JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .type(JOSEObjectType.JWT).keyID(keys.signingKeyId()).build(), claims);
            jwt.sign(new RSASSASigner(keys.signingKey()));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("Cannot sign token", e);
        }
    }

    private JWTClaimsSet verify(String token, String expectedType) {
        if (token == null || token.isBlank()) {
            throw new InvalidTokenException("Missing token");
        }
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
                throw new InvalidTokenException("Unsupported algorithm");
            }
            var publicKey = keys.verificationKey(jwt.getHeader().getKeyID());
            if (publicKey == null) {
                throw new InvalidTokenException("Unknown signing key");
            }
            if (!jwt.verify(new RSASSAVerifier(publicKey))) {
                throw new InvalidTokenException("Bad signature");
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (!settings.issuer().equals(claims.getIssuer())) {
                throw new InvalidTokenException("Wrong issuer");
            }
            if (!expectedType.equals(claims.getStringClaim("typ"))) {
                throw new InvalidTokenException("Wrong token type");
            }
            Date expiry = claims.getExpirationTime();
            if (expiry == null || !expiry.toInstant().isAfter(Instant.now(clock))) {
                throw new InvalidTokenException("Token expired");
            }
            return claims;
        } catch (ParseException | JOSEException e) {
            throw new InvalidTokenException("Unreadable token");
        }
    }

    private static List<String> stringList(JWTClaimsSet claims, String name) throws ParseException {
        List<String> values = claims.getStringListClaim(name);
        return values == null ? List.of() : values;
    }
}
