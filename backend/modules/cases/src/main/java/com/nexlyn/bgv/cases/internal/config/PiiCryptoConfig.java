package com.nexlyn.bgv.cases.internal.config;

import com.nexlyn.bgv.common.crypto.AesGcmEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Encryption of Aadhaar, PAN and other sensitive check values at rest (CLAUDE.md {@literal §11.4}). */
@Configuration
public class PiiCryptoConfig {

    private static final Logger log = LoggerFactory.getLogger(PiiCryptoConfig.class);

    /**
     * Key from {@code PII_ENCRYPTION_KEY} (Base64 of 32 bytes). Prod refuses to start without it.
     * Elsewhere a temporary key is used, which makes every already-stored sensitive value unreadable
     * after a restart, so set the variable for anything but a quick test.
     */
    @Bean
    public AesGcmEncryptor piiEncryptor(@Value("${nexlyn.cases.pii-encryption-key:}") String key, Environment environment) {
        if (key == null || key.isBlank()) {
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException("PII_ENCRYPTION_KEY is required in the prod profile");
            }
            log.warn("PII_ENCRYPTION_KEY is not set: using a temporary key. "
                    + "Sensitive values entered now cannot be read again after the app restarts.");
            return AesGcmEncryptor.fromBase64(AesGcmEncryptor.generateBase64Key());
        }
        return AesGcmEncryptor.fromBase64(key);
    }
}
