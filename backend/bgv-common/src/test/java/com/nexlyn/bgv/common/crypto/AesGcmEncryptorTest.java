package com.nexlyn.bgv.common.crypto;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmEncryptorTest {

    private final AesGcmEncryptor encryptor = AesGcmEncryptor.fromBase64(AesGcmEncryptor.generateBase64Key());

    @Test
    void roundTripsText() {
        String secret = "JBSWY3DPEHPK3PXP";
        assertThat(encryptor.decrypt(encryptor.encrypt(secret))).isEqualTo(secret);
    }

    @Test
    void doesNotExposeThePlaintextAndUsesAFreshIvEachTime() {
        String a = encryptor.encrypt("JBSWY3DPEHPK3PXP");
        String b = encryptor.encrypt("JBSWY3DPEHPK3PXP");
        assertThat(a).isNotEqualTo(b).doesNotContain("JBSWY3DPEHPK3PXP");
    }

    @Test
    void detectsTampering() {
        byte[] bytes = Base64.getDecoder().decode(encryptor.encrypt("secret value"));
        bytes[bytes.length - 1] ^= 0x01;
        String tampered = Base64.getEncoder().encodeToString(bytes);
        assertThatThrownBy(() -> encryptor.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void anotherKeyCannotDecrypt() {
        AesGcmEncryptor other = AesGcmEncryptor.fromBase64(AesGcmEncryptor.generateBase64Key());
        String token = encryptor.encrypt("secret value");
        assertThatThrownBy(() -> other.decrypt(token)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsKeysThatAreNotExactly256Bits() {
        assertThatThrownBy(() -> new AesGcmEncryptor(new byte[16])).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> AesGcmEncryptor.fromBase64("not base64 !!")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsGarbageAndTooShortInput() {
        assertThatThrownBy(() -> encryptor.decrypt("AAAA")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> encryptor.decrypt("%%%")).isInstanceOf(IllegalStateException.class);
    }
}
