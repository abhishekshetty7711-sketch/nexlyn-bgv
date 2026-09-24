package com.nexlyn.bgv.reports.internal.render;

import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.common.error.ErrorCode;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Protects a finished report (CLAUDE.md section 13, step 6): AES-256, a random owner password that nobody
 * ever sees, an optional password to open the file, and permissions that allow printing and reading aloud
 * but not editing, copying text, or assembling pages.
 *
 * <p>If protection fails for any reason the call fails. It never returns the unprotected file as if it
 * were protected (the reference tool could do exactly that, CLAUDE.md section 6.4 item 4).
 */
@Component
public class PdfEncryptionService {

    private static final Logger log = LoggerFactory.getLogger(PdfEncryptionService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    /** @param openPassword the password needed to open the file, or null / blank to open without one */
    public byte[] protect(byte[] pdf, String openPassword) {
        AccessPermission permissions = new AccessPermission();
        permissions.setCanPrint(true);
        permissions.setCanPrintFaithful(true);                 // high-resolution printing
        permissions.setCanExtractForAccessibility(true);       // screen readers
        permissions.setCanModify(false);
        permissions.setCanModifyAnnotations(false);
        permissions.setCanFillInForm(false);
        permissions.setCanExtractContent(false);
        permissions.setCanAssembleDocument(false);

        try (PDDocument document = Loader.loadPDF(pdf); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            String user = openPassword == null ? "" : openPassword;
            StandardProtectionPolicy policy = new StandardProtectionPolicy(randomOwnerPassword(), user, permissions);
            policy.setEncryptionKeyLength(256);
            policy.setPreferAES(true);
            document.protect(policy);
            document.save(out);
            return out.toByteArray();
        } catch (IOException | RuntimeException e) {
            log.error("Protecting a PDF failed: {}", e.toString());
            throw new ApiException(ErrorCode.SERVICE_UNAVAILABLE, "The PDF could not be protected, so it was not produced.");
        }
    }

    private static String randomOwnerPassword() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
