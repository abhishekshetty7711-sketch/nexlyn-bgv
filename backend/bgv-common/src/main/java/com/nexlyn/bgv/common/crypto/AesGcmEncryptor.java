package com.nexlyn.bgv.common.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM authenticated encryption for secrets stored in the database (2FA secrets now,
 * PII later). Output is Base64 of {@code iv (12 bytes) || ciphertext || tag}; a fresh random IV is
 * used for every call. Tampering or a wrong key makes {@link #decrypt} fail loudly.
 */
public final class AesGcmEncryptor {

    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmEncryptor(byte[] keyBytes) {
        if (keyBytes == null || keyBytes.length != KEY_BYTES) {
            throw new IllegalArgumentException("AES-256 key must be exactly " + KEY_BYTES + " bytes");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    /** Key supplied as Base64 text, e.g. from an environment variable. */
    public static AesGcmEncryptor fromBase64(String base64Key) {
        try {
            return new AesGcmEncryptor(Base64.getDecoder().decode(base64Key.trim()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Encryption key must be Base64 of exactly " + KEY_BYTES + " bytes", e);
        }
    }

    /** A fresh random key, Base64 encoded. Useful for tests and for generating dev keys. */
    public static String generateBase64Key() {
        byte[] bytes = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    public String decrypt(String token) {
        try {
            byte[] all = Base64.getDecoder().decode(token);
            if (all.length <= IV_BYTES) {
                throw new IllegalStateException("Ciphertext is too short");
            }
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, all, 0, IV_BYTES));
            return new String(cipher.doFinal(all, IV_BYTES, all.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            // Never include the token or key material in the message.
            throw new IllegalStateException("Decryption failed (wrong key or tampered data)", e);
        }
    }
}
