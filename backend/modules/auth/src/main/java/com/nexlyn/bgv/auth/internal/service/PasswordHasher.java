package com.nexlyn.bgv.auth.internal.service;

import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Argon2id password hashing with the OWASP-recommended minimum parameters
 * (19 MiB memory, 2 iterations, 1 lane). The salt is random and stored inside the hash string.
 */
@Component
public class PasswordHasher {

    private final Argon2PasswordEncoder encoder = new Argon2PasswordEncoder(16, 32, 1, 19_456, 2);

    /** A real hash of a throwaway value, verified against when the account does not exist (see {@link #verifyDummy}). */
    private final String dummyHash = encoder.encode("dummy-value-for-constant-time-checks");

    public String hash(String rawPassword) {
        return encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String hash) {
        return encoder.matches(rawPassword, hash);
    }

    /**
     * Burns the same CPU time as a real check so response time does not reveal whether an
     * email address is registered.
     */
    public void verifyDummy(String rawPassword) {
        encoder.matches(rawPassword, dummyHash);
    }
}
