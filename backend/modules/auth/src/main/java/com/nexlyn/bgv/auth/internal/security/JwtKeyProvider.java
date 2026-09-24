package com.nexlyn.bgv.auth.internal.security;

import com.nexlyn.bgv.auth.internal.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Holds the RS256 key used to sign new tokens plus every public key still accepted for
 * verification (the current one and any {@code previous-keys}, so keys can be rotated: sign with a
 * new {@code kid}, keep the old public key listed until its tokens have expired).
 *
 * <p>Prod refuses to start without a key. Other profiles generate a temporary key pair, which
 * invalidates all tokens on restart.
 */
@Component
public class JwtKeyProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtKeyProvider.class);

    private final RSAPrivateKey signingKey;
    private final String signingKeyId;
    private final Map<String, RSAPublicKey> verificationKeys = new LinkedHashMap<>();

    public JwtKeyProvider(AuthProperties properties, Environment environment) {
        AuthProperties.Jwt jwt = properties.jwt();
        if (isBlank(jwt.privateKey())) {
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException("JWT_PRIVATE_KEY is required in the prod profile");
            }
            KeyPair pair = generate();
            this.signingKey = (RSAPrivateKey) pair.getPrivate();
            this.signingKeyId = "ephemeral-" + UUID.randomUUID().toString().substring(0, 8);
            this.verificationKeys.put(signingKeyId, (RSAPublicKey) pair.getPublic());
            log.warn("JWT_PRIVATE_KEY is not set: using a temporary signing key. "
                    + "All sessions end when the app restarts. Set JWT_PRIVATE_KEY for anything but a quick test.");
        } else {
            this.signingKey = parsePrivateKey(jwt.privateKey());
            this.signingKeyId = isBlank(jwt.keyId()) ? "default" : jwt.keyId().trim();
            RSAPublicKey derived = derivePublicKey(signingKey);
            if (!isBlank(jwt.publicKey()) && !derived.getModulus().equals(parsePublicKey(jwt.publicKey()).getModulus())) {
                throw new IllegalStateException("JWT_PUBLIC_KEY does not match JWT_PRIVATE_KEY");
            }
            this.verificationKeys.put(signingKeyId, derived);
        }
        for (AuthProperties.PreviousKey previous : jwt.previousKeys()) {
            verificationKeys.put(previous.keyId(), parsePublicKey(previous.publicKey()));
        }
    }

    public RSAPrivateKey signingKey() {
        return signingKey;
    }

    public String signingKeyId() {
        return signingKeyId;
    }

    /** The public key for a {@code kid}, or null if that key is not trusted. */
    public RSAPublicKey verificationKey(String keyId) {
        return keyId == null ? null : verificationKeys.get(keyId);
    }

    private static KeyPair generate() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot generate an RSA key pair", e);
        }
    }

    private static RSAPrivateKey parsePrivateKey(String pem) {
        try {
            return (RSAPrivateKey) KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decodePem(pem)));
        } catch (GeneralSecurityException | ClassCastException e) {
            throw new IllegalStateException("JWT_PRIVATE_KEY is not a valid PKCS#8 RSA private key", e);
        }
    }

    private static RSAPublicKey parsePublicKey(String pem) {
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decodePem(pem)));
        } catch (GeneralSecurityException | ClassCastException e) {
            throw new IllegalStateException("A JWT public key is not a valid X.509 RSA public key", e);
        }
    }

    private static RSAPublicKey derivePublicKey(RSAPrivateKey privateKey) {
        if (!(privateKey instanceof RSAPrivateCrtKey crt)) {
            throw new IllegalStateException("JWT_PRIVATE_KEY must be an RSA CRT key (a normal PKCS#8 RSA key)");
        }
        try {
            return (RSAPublicKey) KeyFactory.getInstance("RSA")
                    .generatePublic(new RSAPublicKeySpec(crt.getModulus(), crt.getPublicExponent()));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Cannot derive the public key", e);
        }
    }

    /** Accepts PEM with real or backslash-n escaped newlines (as in an .env file), or bare Base64. */
    static byte[] decodePem(String pem) {
        String base64 = pem
                .replaceAll("-----[A-Z ]+-----", "")
                .replace("\\n", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(base64);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
