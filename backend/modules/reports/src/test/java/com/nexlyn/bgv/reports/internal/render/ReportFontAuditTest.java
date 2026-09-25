package com.nexlyn.bgv.reports.internal.render;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The font audit itself, on PDFs made here (no browser needed). */
class ReportFontAuditTest {

    private static byte[] pdfWithHelvetica() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(50, 700);
                content.showText("NEXLYN SERVICES");
                content.endText();
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    @Test
    void anUnembeddedFontThatIsNotABundledOneIsReportedTwice() throws Exception {
        var result = ReportFontAudit.inspect(pdfWithHelvetica(), ReportFontAudit.BUNDLED);
        assertThat(result.ok()).isFalse();
        assertThat(result.families()).containsExactly("Helvetica");
        assertThat(result.problems()).anySatisfy(p -> assertThat(p).contains("not embedded"))
                .anySatisfy(p -> assertThat(p).contains("not one of the bundled report fonts"));
    }

    @Test
    void theFamilyOfAFontNameHasTheSubsetPrefixAndTheStyleRemoved() {
        assertThat(ReportFontAudit.family("ABCDEF+Selawik-Bold")).isEqualTo("Selawik");
        assertThat(ReportFontAudit.family("AAAAAA+LiberationSans")).isEqualTo("LiberationSans");
        assertThat(ReportFontAudit.family("Inter-SemiBold")).isEqualTo("Inter");
        assertThat(ReportFontAudit.family(null)).isEqualTo("(unnamed font)");
    }

    @Test
    void wordsAreFoundWhateverFontDrewThem() throws Exception {
        assertThat(ReportFontAudit.familiesOfWords(pdfWithHelvetica(), 1, List.of("NEXLYN", "SERVICES", "MISSING")))
                .containsEntry("NEXLYN", "Helvetica").containsEntry("SERVICES", "Helvetica").doesNotContainKey("MISSING");
    }

    @Test
    void theSamplePageCheckNamesEveryProblemOfAWrongPdf() throws Exception {
        List<String> problems = ReportFontAudit.checkSamplePage(pdfWithHelvetica());
        assertThat(problems).anySatisfy(p -> assertThat(p).contains("Helvetica"))
                .anySatisfy(p -> assertThat(p).contains("\"NEXLYN\" is drawn in Helvetica, expected Selawik"))
                .anySatisfy(p -> assertThat(p).contains("\"TRUST\" was not found"));
    }
}
