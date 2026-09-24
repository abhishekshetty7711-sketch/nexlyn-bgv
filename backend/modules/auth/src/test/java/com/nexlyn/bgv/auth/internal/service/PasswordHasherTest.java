package com.nexlyn.bgv.auth.internal.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void producesAnArgon2idHashThatVerifies() {
        String hash = hasher.hash("Correct-Horse-9-Battery");
        assertThat(hash).startsWith("$argon2id$");
        assertThat(hasher.matches("Correct-Horse-9-Battery", hash)).isTrue();
    }

    @Test
    void rejectsAWrongPassword() {
        String hash = hasher.hash("Correct-Horse-9-Battery");
        assertThat(hasher.matches("Correct-Horse-9-Batterx", hash)).isFalse();
        assertThat(hasher.matches("", hash)).isFalse();
    }

    @Test
    void usesARandomSaltSoEqualPasswordsGiveDifferentHashes() {
        assertThat(hasher.hash("Correct-Horse-9-Battery")).isNotEqualTo(hasher.hash("Correct-Horse-9-Battery"));
    }

    @Test
    void neverStoresThePlainPasswordInTheHash() {
        assertThat(hasher.hash("Correct-Horse-9-Battery")).doesNotContain("Correct-Horse");
    }
}
