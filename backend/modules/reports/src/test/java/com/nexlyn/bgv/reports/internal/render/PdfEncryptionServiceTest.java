package com.nexlyn.bgv.reports.internal.render;

import com.nexlyn.bgv.common.error.ApiException;
import com.nexlyn.bgv.common.error.ErrorCode;
import com.nexlyn.bgv.reports.ReportFixtures;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfEncryptionServiceTest {

    private final PdfEncryptionService service = new PdfEncryptionService();
    private final byte[] plain = ReportFixtures.blankPdf(2);

    @Test
    void withAPasswordTheFileCannotBeOpenedWithoutIt() throws IOException {
        byte[] protectedPdf = service.protect(plain, "open-sesame-2026");

        assertThatThrownBy(() -> Loader.loadPDF(protectedPdf)).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> Loader.loadPDF(protectedPdf, "wrong")).isInstanceOf(IOException.class);
        try (PDDocument document = Loader.loadPDF(protectedPdf, "open-sesame-2026")) {
            assertThat(document.getNumberOfPages()).isEqualTo(2);
            assertThat(document.isEncrypted()).isTrue();
        }
    }

    @Test
    void itIsAes256() throws IOException {
        byte[] protectedPdf = service.protect(plain, "open-sesame-2026");
        try (PDDocument document = Loader.loadPDF(protectedPdf, "open-sesame-2026")) {
            assertThat(document.getEncryption().getLength()).isEqualTo(256);
            assertThat(document.getEncryption().getFilter()).isEqualTo("Standard");
            assertThat(document.getEncryption().getVersion()).isEqualTo(5);
        }
    }

    @Test
    void withoutAPasswordItOpensButIsStillLockedAgainstChanges() throws IOException {
        byte[] protectedPdf = service.protect(plain, null);
        try (PDDocument document = Loader.loadPDF(protectedPdf)) {
            assertThat(document.isEncrypted()).isTrue();
            AccessPermission permissions = document.getCurrentAccessPermission();
            assertThat(permissions.canPrint()).isTrue();
            assertThat(permissions.canPrintFaithful()).as("high-resolution printing").isTrue();
            assertThat(permissions.canExtractForAccessibility()).isTrue();
            assertThat(permissions.canModify()).isFalse();
            assertThat(permissions.canExtractContent()).isFalse();
            assertThat(permissions.canAssembleDocument()).isFalse();
            assertThat(permissions.canModifyAnnotations()).isFalse();
            assertThat(permissions.canFillInForm()).isFalse();
        }
    }

    @Test
    void theOwnerPasswordIsRandomPerFile() throws IOException {
        byte[] a = service.protect(plain, null);
        byte[] b = service.protect(plain, null);
        assertThat(a).isNotEqualTo(b);
        try (PDDocument first = Loader.loadPDF(a)) {
            assertThat(first.getEncryption()).isNotNull();
        }
    }

    @Test
    void aBrokenInputFailsLoudlyInsteadOfReturningItUnprotected() {
        assertThatThrownBy(() -> service.protect("not a pdf".getBytes(), "pw"))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.code()).isEqualTo(ErrorCode.SERVICE_UNAVAILABLE);
                    assertThat(e.getMessage()).contains("not produced");
                });
    }
}
