package com.nexlyn.bgv.auth.internal.service;

import com.nexlyn.bgv.auth.internal.config.AuthProperties;
import com.nexlyn.bgv.auth.internal.domain.Admin;
import com.nexlyn.bgv.auth.internal.domain.BackupCode;
import com.nexlyn.bgv.auth.internal.domain.TotpSecret;
import com.nexlyn.bgv.auth.internal.repository.BackupCodeRepository;
import com.nexlyn.bgv.auth.internal.repository.TotpSecretRepository;
import com.nexlyn.bgv.common.crypto.AesGcmEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Enrolment and checking of the second factor: an authenticator-app code (TOTP) or one of ten
 * single-use backup codes. A TOTP code is accepted at most once (its time step must be newer than
 * the last accepted one). Callers count failures towards lockout; this class only reports a result.
 */
@Service
public class TwoFactorService {

    /** Result of checking a submitted code. */
    public enum CodeResult { TOTP_ACCEPTED, BACKUP_CODE_ACCEPTED, REJECTED }

    /** What the admin needs to enrol: the Base32 secret and the {@code otpauth://} link for a QR code. */
    public record SetupInfo(String secret, String otpauthUri) {
    }

    // No look-alike characters (0/O, 1/I): backup codes get read aloud and typed by hand.
    private static final String BACKUP_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int BACKUP_CODE_COUNT = 10;
    private static final int BACKUP_CODE_LENGTH = 10;

    private final TotpService totp;
    private final TotpSecretRepository secrets;
    private final BackupCodeRepository backupCodes;
    private final AesGcmEncryptor encryptor;
    private final PasswordHasher hasher;
    private final AuthProperties.Totp settings;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TwoFactorService(TotpService totp, TotpSecretRepository secrets, BackupCodeRepository backupCodes,
                            AesGcmEncryptor encryptor, PasswordHasher hasher, AuthProperties properties, Clock clock) {
        this.totp = totp;
        this.secrets = secrets;
        this.backupCodes = backupCodes;
        this.encryptor = encryptor;
        this.hasher = hasher;
        this.settings = properties.totp();
        this.clock = clock;
    }

    /** Starts (or restarts) enrolment. Replaces any earlier unconfirmed secret; never touches a confirmed one. */
    @Transactional
    public SetupInfo beginSetup(Admin admin) {
        Instant now = Instant.now(clock);
        String secret = totp.generateSecret();
        String encrypted = encryptor.encrypt(secret);
        TotpSecret row = secrets.findById(admin.getId()).orElse(null);
        if (row == null) {
            secrets.save(new TotpSecret(admin.getId(), encrypted, now));
        } else if (!row.isConfirmed()) {
            row.replaceSecret(encrypted, now);
            secrets.save(row);
        } else {
            throw new IllegalStateException("Two-factor authentication is already set up for this admin");
        }
        return new SetupInfo(secret, totp.otpauthUri(settings.issuer(), admin.getEmail(), secret));
    }

    /**
     * Confirms enrolment with the first code from the app. On success turns 2FA on and returns
     * ten new backup codes (shown once, never retrievable again); on a wrong code returns empty.
     */
    @Transactional
    public Optional<List<String>> confirmSetup(Admin admin, String code) {
        TotpSecret row = secrets.findById(admin.getId()).filter(s -> !s.isConfirmed()).orElse(null);
        if (row == null) {
            return Optional.empty();
        }
        OptionalLong step = totp.findMatchingStep(encryptor.decrypt(row.getSecretEncrypted()), code, Instant.now(clock));
        if (step.isEmpty()) {
            return Optional.empty();
        }
        Instant now = Instant.now(clock);
        row.confirm(now);
        row.setLastUsedStep(step.getAsLong());
        secrets.save(row);
        admin.setMfaEnabled(true);
        return Optional.of(replaceBackupCodes(admin));
    }

    /** Checks a login code: a 6-digit TOTP code, or a backup code (dashes and case ignored). */
    @Transactional
    public CodeResult verify(Admin admin, String submitted) {
        if (submitted == null) {
            return CodeResult.REJECTED;
        }
        String code = submitted.trim();
        if (code.matches("\\d{6}")) {
            return verifyTotp(admin, code) ? CodeResult.TOTP_ACCEPTED : CodeResult.REJECTED;
        }
        return verifyBackupCode(admin, code) ? CodeResult.BACKUP_CODE_ACCEPTED : CodeResult.REJECTED;
    }

    private boolean verifyTotp(Admin admin, String code) {
        TotpSecret row = secrets.findById(admin.getId()).filter(TotpSecret::isConfirmed).orElse(null);
        if (row == null) {
            return false;
        }
        OptionalLong step = totp.findMatchingStep(encryptor.decrypt(row.getSecretEncrypted()), code, Instant.now(clock));
        if (step.isEmpty()) {
            return false;
        }
        // Replay protection: a code from a step that was already used (or an older one) is refused.
        if (row.getLastUsedStep() != null && step.getAsLong() <= row.getLastUsedStep()) {
            return false;
        }
        row.setLastUsedStep(step.getAsLong());
        secrets.save(row);
        return true;
    }

    private boolean verifyBackupCode(Admin admin, String submitted) {
        String normalized = normalizeBackupCode(submitted);
        if (normalized.length() != BACKUP_CODE_LENGTH) {
            return false;
        }
        for (BackupCode candidate : backupCodes.findByAdminIdAndUsedAtIsNull(admin.getId())) {
            if (hasher.matches(normalized, candidate.getCodeHash())) {
                candidate.markUsed(Instant.now(clock));
                backupCodes.save(candidate);
                return true;
            }
        }
        return false;
    }

    private List<String> replaceBackupCodes(Admin admin) {
        backupCodes.deleteAllForAdmin(admin.getId());
        List<String> shown = new ArrayList<>();
        for (int i = 0; i < BACKUP_CODE_COUNT; i++) {
            String code = randomBackupCode();
            backupCodes.save(new BackupCode(admin.getId(), hasher.hash(code)));
            shown.add(code.substring(0, 5) + "-" + code.substring(5));
        }
        return shown;
    }

    private String randomBackupCode() {
        StringBuilder code = new StringBuilder(BACKUP_CODE_LENGTH);
        for (int i = 0; i < BACKUP_CODE_LENGTH; i++) {
            code.append(BACKUP_ALPHABET.charAt(random.nextInt(BACKUP_ALPHABET.length())));
        }
        return code.toString();
    }

    private static String normalizeBackupCode(String submitted) {
        return submitted.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }
}
