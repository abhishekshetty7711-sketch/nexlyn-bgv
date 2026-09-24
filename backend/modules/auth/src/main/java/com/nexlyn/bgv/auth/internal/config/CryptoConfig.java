package com.nexlyn.bgv.auth.internal.config;

import com.nexlyn.bgv.common.crypto.AesGcmEncryptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/** Encryption for 2FA secrets at rest. */
@Configuration
public class CryptoConfig {

    private static final Logger log = LoggerFactory.getLogger(CryptoConfig.class);

    /**
     * Key from {@code TOTP_ENCRYPTION_KEY}. Prod refuses to start without it; elsewhere a temporary
     * key is used, which makes already-enrolled 2FA secrets unreadable after a restart.
     */
    @Bean
    public AesGcmEncryptor totpEncryptor(AuthProperties properties, Environment environment) {
        String key = properties.totp().encryptionKey();
        if (key == null || key.isBlank()) {
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException("TOTP_ENCRYPTION_KEY is required in the prod profile");
            }
            log.warn("TOTP_ENCRYPTION_KEY is not set: using a temporary key. "
                    + "Enrolled 2FA secrets become unreadable when the app restarts.");
            return AesGcmEncryptor.fromBase64(AesGcmEncryptor.generateBase64Key());
        }
        return AesGcmEncryptor.fromBase64(key);
    }
}
