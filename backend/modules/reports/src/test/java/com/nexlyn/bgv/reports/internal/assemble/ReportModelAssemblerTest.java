package com.nexlyn.bgv.reports.internal.assemble;

import com.nexlyn.bgv.cases.CaseReport;
import com.nexlyn.bgv.cases.CaseReport.Check;
import com.nexlyn.bgv.common.enums.CheckStatus;
import com.nexlyn.bgv.documents.DocumentApi.Crop;
import com.nexlyn.bgv.reports.ReportFixtures;
import com.nexlyn.bgv.reports.ReportFixtures.StubDocuments;
import com.nexlyn.bgv.reports.internal.model.ReportPages.Cover;
import com.nexlyn.bgv.reports.internal.model.ReportPages.DetailPage;
import com.nexlyn.bgv.reports.internal.model.ReportPages.DocumentPage;
import com.nexlyn.bgv.reports.internal.model.ReportPages.OverflowPage;
import com.nexlyn.bgv.reports.internal.model.ReportPages.Page;
import com.nexlyn.bgv.reports.internal.model.ReportPages.RemarksPage;
import com.nexlyn.bgv.reports.internal.model.ReportPages.ReportDocument;
import com.nexlyn.bgv.reports.internal.render.ImageEmbedder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportModelAssemblerTest {

    StubDocuments documents;
    ReportModelAssembler assembler;

    @BeforeEach
    void setUp() {
        documents = new StubDocuments();
        assembler = new ReportModelAssembler(documents, new ImageEmbedder(2400), new AssetSource());
    }

    private static List<String> kinds(ReportDocument doc) {
        return doc.pages().stream().map(Page::kind).toList();
    }

    // ---- the page sequence -----------------------------------------------------------------------------

    @Test
    void twoChecksAreCoverTwoDetailPagesAndServices() {
        Check id = ReportFixtures.check("AADHAAR", "identity", "Identity Verification (Aadhaar)", CheckStatus.VERIFIED);
        Check court = ReportFixtures.check("COURT", "court", "Court Record", CheckStatus.VERIFIED);
        ReportDocument doc = assembler.assemble(ReportFixtures.report(List.of(id, court), null));

        assertThat(kinds(doc)).containsExactly("cover", "detail", "detail", "services");
        assertThat(doc.pages()).extracting(Page::number).containsExactly(1, 2, 3, 4);
        assertThat(doc.pages()).extracting(Page::total).containsOnly(4);
        Cover cover = (Cover) doc.pages().get(0);
        assertThat(cover.inlineRemarks()).as("two groups: remarks on page 1").isNotNull();
        assertThat(cover.summary()).extracting(c -> c.title()).containsExactly("Identity Verification (Aadhaar)", "Court Record");
    }

    @Test
    void threeGroupsPutTheRemarksOnPageTwo() {
        ReportDocument doc = assembler.assemble(ReportFixtures.report(ReportFixtures.manyGroups(3), null));
        assertThat(kinds(doc)).containsExactly("cover", "remarks", "detail", "detail", "detail", "services");
        assertThat(((Cover) doc.pages().get(0)).inlineRemarks()).isNull();
        RemarksPage remarks = (RemarksPage) doc.pages().get(1);
        assertThat(remarks.number()).isEqualTo(2);
        assertThat(remarks.remarks().analystHtml()).isEqualTo("All checks were <strong>completed</strong>.");
        assertThat(remarks.remarks().finalHtml()).isEqualTo("Approved for the next stage.");
    }

    @Test
    void fiveGroupsOverflowOntoAnExtraSummaryPageThatCarriesTheRemarks() {
        ReportDocument doc = assembler.assemble(ReportFixtures.report(ReportFixtures.manyGroups(5), null));
        assertThat(kinds(doc)).containsExactly("cover", "overflow", "detail", "detail", "detail", "detail", "detail", "services");
        assertThat(((Cover) doc.pages().get(0)).summary()).hasSize(4);
        OverflowPage overflow = (OverflowPage) doc.pages().get(1);
        assertThat(overflow.cards()).extracting(c -> c.title()).containsExactly("Check 5");
        assertThat(overflow.remarks()).isNotNull();
    }

    @Test
    void theSixCardLayoutIsCompact() {
        CaseReport report = ReportFixtures.withSettings(ReportFixtures.report(ReportFixtures.manyGroups(6), null), 6, "NUMERIC", false, "");
        ReportDocument doc = assembler.assemble(report);
        Cover cover = (Cover) doc.pages().get(0);
        assertThat(cover.compact()).isTrue();
        assertThat(cover.summary()).hasSize(6);
    }

    // ---- one summary card per group ---------------------------------------------------------------------

    @Test
    void checksOfOneKindShareASummaryCardShowingTheMostSeriousStatus() {
        Check first = ReportFixtures.check("AADHAAR", "identity", "Identity Verification (Aadhaar)", CheckStatus.VERIFIED);
        Check second = ReportFixtures.check("PAN", "identity", "Identity Verification (PAN)", CheckStatus.DISCREPANCY);
        ReportDocument doc = assembler.assemble(ReportFixtures.report(List.of(first, second), null));

        Cover cover = (Cover) doc.pages().get(0);
        assertThat(cover.summary()).hasSize(1);
        assertThat(cover.summary().get(0).title()).isEqualTo("Identity Verification (Aadhaar)");
        assertThat(cover.summary().get(0).status().label()).isEqualTo("Discrepancy");
        assertThat(kinds(doc)).as("each check still has its own detail page").containsExactly("cover", "detail", "detail", "services");
    }

    // ---- page 1 ---------------------------------------------------------------------------------------------

    @Test
    void theCoverShowsFormattedValuesAndDashesForMissingOnes() {
        Check check = ReportFixtures.check("AADHAAR", "identity", "Identity", CheckStatus.VERIFIED);
        CaseReport report = ReportFixtures.report(List.of(check), null);
        Cover cover = (Cover) assembler.assemble(report).pages().get(0);

        assertThat(cover.reportId()).isEqualTo("NX-2026-0142");
        assertThat(cover.issueDate()).isEqualTo("11/06/2026");
        assertThat(cover.companyName()).isEqualTo("Acme Corp\nPrivate Limited");
        assertThat(cover.dob()).isEqualTo("17/05/1994");
        assertThat(cover.phone()).isEqualTo("+91 98765 43210");
        assertThat(cover.periodStart()).isEqualTo("19/05/2026");
        assertThat(cover.overviewTotal()).isEqualTo("01");
        assertThat(cover.overviewStatus()).isEqualTo("Clear");
        assertThat(cover.parentLabel()).isEqualTo("Father Name");
        assertThat(cover.photoSrc()).isNull();
    }

    @Test
    void textDatesUseTheMonthName() {
        Check check = ReportFixtures.check("AADHAAR", "identity", "Identity", CheckStatus.VERIFIED);
        CaseReport report = ReportFixtures.withSettings(ReportFixtures.report(List.of(check), null), 4, "TEXT", false, "");
        Cover cover = (Cover) assembler.assemble(report).pages().get(0);
        assertThat(cover.issueDate()).isEqualTo("11-Jun-2026");
        assertThat(cover.dob()).isEqualTo("17-May-1994");
        DetailPage detail = (DetailPage) assembler.assemble(report).pages().get(1);
        assertThat(detail.rows().get(1).value()).isEqualTo("17-May-1994");
        assertThat(detail.details()).extracting(d -> d.value()).contains("19-May-2026", "11-Jun-2026");
    }

    @Test
    void aGuardianChangesTheLabelAndTheHiddenPeriodDisappears() {
        Check check = ReportFixtures.check("AADHAAR", "identity", "Identity", CheckStatus.VERIFIED);
        CaseReport base = ReportFixtures.report(List.of(check), null);
        CaseReport report = new CaseReport(base.caseId(), base.reportId(), base.lifecycle(), base.issueDate(), base.companyName(),
                new CaseReport.Candidate("Asha Rao", true, "Uncle Rao", "E1", base.candidate().dob(), null, null),
                new CaseReport.Period(false, null, null), base.pill(), base.overview(), null, null, base.settings(), base.checks());
        Cover cover = (Cover) assembler.assemble(report).pages().get(0);

        assertThat(cover.parentLabel()).isEqualTo("Guardian Name");
        assertThat(cover.showPeriod()).isFalse();
        assertThat(cover.phone()).as("missing values print a dash").isEqualTo("—");
        assertThat(cover.inlineRemarks().analystHtml()).isEqualTo("—");
    }

    @Test
    void thePhotoIsEmbeddedWhenThereIsOne() {
        Check check = ReportFixtures.check("AADHAAR", "identity", "Identity", CheckStatus.VERIFIED);
        var photo = documents.photo(ReportFixtures.png(120, 150, Color.LIGHT_GRAY));
        Cover cover = (Cover) assembler.assemble(ReportFixtures.report(List.of(check), photo)).pages().get(0);
        assertThat(cover.photoSrc()).startsWith("data:image/png;base64,");
    }

    @Test
    void aMissingPhotoFileFallsBackToThePlaceholderInsteadOfFailingTheReport() {
        Check check = ReportFixtures.check("AADHAAR", "identity", "Identity", CheckStatus.VERIFIED);
        var photo = documents.photo(ReportFixtures.png(120, 150, Color.LIGHT_GRAY));
        documents.lose(photo);
        // The store cannot return the bytes: the report still comes out, with the placeholder.
        Cover cover = (Cover) assembler.assemble(ReportFixtures.report(List.of(check), photo)).pages().get(0);
        assertThat(cover.photoSrc()).isNull();
    }

    // ---- a check's detail page -----------------------------------------------------------------------------

    @Test
    void theDetailPageHasChecksDetailsRemarksAttestationAndBlocks() {
        Check base = ReportFixtures.check("COURT", "court", "Court Record", CheckStatus.UNABLE_TO_VERIFY);
        Check check = ReportFixtures.withAttestation(ReportFixtures.withRemarks(base, "No <script>x</script> records"), "", "");
        check = ReportFixtures.withFields(check,
                List.of(new CaseReport.Field("Full Name", "text", "Asha Rao", true),
                        new CaseReport.Field("Result", "boolean", "true", false),
                        new CaseReport.Field("Gap Periods", "repeatable", "[{\"from\":\"2020-01-01\",\"to\":\"2020-03-01\",\"reason\":\"Break\"}]", false),
                        new CaseReport.Field("Aadhaar Number", "aadhaar", "XXXX XXXX 0124", true)),
                List.of(new CaseReport.Detail("Court Type", "High Court"), new CaseReport.Detail("Search Date", "2026-06-01")),
                List.of(new CaseReport.FreeBlock("TEXT", "Searched twice.", null), new CaseReport.FreeBlock("TEXT", "  ", null)));
        ReportDocument doc = assembler.assemble(ReportFixtures.report(List.of(check), null));

        DetailPage page = (DetailPage) doc.pages().get(1);
        assertThat(page.title().status().label()).isEqualTo("Unable to Verify");
        assertThat(page.title().docName()).isEqualTo("Verifies Court Record");
        assertThat(page.rows()).extracting(r -> r.label() + "=" + r.value() + (r.tick() ? "+" : ""))
                .containsExactly("Full Name=Asha Rao+", "Result=Yes", "Gap Periods=01/01/2020 · 01/03/2020 · Break",
                        "Aadhaar Number=XXXX XXXX 0124+");
        assertThat(page.details()).extracting(d -> d.label() + "=" + d.value())
                .containsExactly("Verification Type=Standard", "Document Type=Document of Court Record",
                        "Requested Date=19/05/2026", "Completed Date=11/06/2026", "Court Type=High Court", "Search Date=01/06/2026");
        assertThat(page.remarksHtml()).as("only bold survives").isEqualTo("No &lt;script&gt;x&lt;/script&gt; records");
        assertThat(page.attestation().barCouncil()).isEqualTo(ReportModelAssembler.DEFAULT_BAR_COUNCIL);
        assertThat(page.attestation().disclaimer()).isEqualTo(ReportModelAssembler.DEFAULT_DISCLAIMER);
        assertThat(page.attestation().sealSrc()).startsWith("data:image/png;base64,");
        assertThat(page.freeBlocks()).as("a blank text block is dropped").hasSize(1);
    }

    @Test
    void anAttestationKeepsItsOwnNumberAndWording() {
        Check check = ReportFixtures.withAttestation(ReportFixtures.check("COURT", "court", "Court", CheckStatus.VERIFIED), "KAR/1/99", "Custom words.");
        DetailPage page = (DetailPage) assembler.assemble(ReportFixtures.report(List.of(check), null)).pages().get(1);
        assertThat(page.attestation().barCouncil()).isEqualTo("KAR/1/99");
        assertThat(page.attestation().disclaimer()).isEqualTo("Custom words.");
    }

    @Test
    void noAttestationMeansNoBlock() {
        Check check = ReportFixtures.check("AADHAAR", "identity", "Identity", CheckStatus.VERIFIED);
        DetailPage page = (DetailPage) assembler.assemble(ReportFixtures.report(List.of(check), null)).pages().get(1);
        assertThat(page.attestation()).isNull();
        assertThat(page.remarksHtml()).isNull();
        assertThat(page.frames()).isEmpty();
    }

    // ---- supporting documents ---------------------------------------------------------------------------------

    @Test
    void documentsAreNumberedInPageOrderMovedOnesContinueTheCount() {
        Check check = ReportFixtures.check("AADHAAR", "identity", "Identity", CheckStatus.VERIFIED);
        documents.attach(check.id(), "image/png", ReportFixtures.png(100, 80, Color.WHITE), true, false, null);   // moved to its own page
        documents.attach(check.id(), "image/png", ReportFixtures.png(100, 80, Color.WHITE), false, false, null);  // stays
        documents.attach(check.id(), "image/jpeg", ReportFixtures.jpeg(100, 80, Color.WHITE), true, true, null);  // moved, larger box
        ReportDocument doc = assembler.assemble(ReportFixtures.report(List.of(check), null));

        assertThat(kinds(doc)).containsExactly("cover", "detail", "document", "document", "services");
        DetailPage detail = (DetailPage) doc.pages().get(1);
        assertThat(detail.frames()).hasSize(1);
        assertThat(detail.frames().get(0).header()).isEqualTo("Document 1 — Verifies Identity — Additional Document 1");
        DocumentPage first = (DocumentPage) doc.pages().get(2);
        DocumentPage second = (DocumentPage) doc.pages().get(3);
        assertThat(first.frame().header()).isEqualTo("Document 2 — Verifies Identity — Original Document");
        assertThat(first.large()).isFalse();
        assertThat(second.frame().header()).startsWith("Document 3 —");
        assertThat(second.large()).isTrue();
        assertThat(first.title().continued()).isTrue();
        assertThat(doc.pages()).extracting(Page::number).containsExactly(1, 2, 3, 4, 5);
    }

    @Test
    void aLargerBoxAlwaysHasAPageOfItsOwnEvenWhenTheMoveFlagIsOff() {
        Check check = ReportFixtures.check("AADHAAR", "identity", "Identity", CheckStatus.VERIFIED);
        documents.attach(check.id(), "image/png", ReportFixtures.png(100, 80, Color.WHITE), false, true, null);   // larger only
        documents.attach(check.id(), "image/png", ReportFixtures.png(100, 80, Color.WHITE), true, true, null);    // both on
        ReportDocument doc = assembler.assemble(ReportFixtures.report(List.of(check), null));

        assertThat(kinds(doc)).containsExactly("cover", "detail", "document", "document", "services");
        assertThat(((DetailPage) doc.pages().get(1)).frames()).isEmpty();
        assertThat(((DocumentPage) doc.pages().get(2)).large()).isTrue();
        assertThat(((DocumentPage) doc.pages().get(3)).large()).isTrue();
        assertThat(((DocumentPage) doc.pages().get(3)).frame().header()).startsWith("Document 2 —");
        assertThat(doc.total()).as("one more page for each").isEqualTo(5);
    }

    @Test
    void aMovedDocumentInTheStandardBoxIsNotLarge() {
        Check check = ReportFixtures.check("AADHAAR", "identity", "Identity", CheckStatus.VERIFIED);
        documents.attach(check.id(), "image/png", ReportFixtures.png(100, 80, Color.WHITE), true, false, null);
        ReportDocument doc = assembler.assemble(ReportFixtures.report(List.of(check), null));
        assertThat(((DocumentPage) doc.pages().get(2)).large()).isFalse();
    }

    @Test
    void aCropIsAppliedToThePictureThatIsEmbedded() throws Exception {
        Check check = ReportFixtures.check("AADHAAR", "identity", "Identity", CheckStatus.VERIFIED);
        documents.attach(check.id(), "image/png", ReportFixtures.png(200, 100, Color.BLUE), false, false, new Crop(0.5, 0, 0.5, 1));
        DetailPage page = (DetailPage) assembler.assemble(ReportFixtures.report(List.of(check), null)).pages().get(1);

        byte[] embedded = java.util.Base64.getDecoder().decode(page.frames().get(0).imageSrc().substring("data:image/png;base64,".length()));
        var image = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(embedded));
        assertThat(image.getWidth()).isEqualTo(100);
        assertThat(image.getHeight()).isEqualTo(100);
        assertThat(new Color(image.getRGB(2, 2)).getBlue()).as("the red corner belonged to the cropped-away half").isGreaterThan(200);
    }

    @Test
    void aPdfIsShownAsItsFirstPageWithANoteAboutTheRest() {
        Check check = ReportFixtures.check("AADHAAR", "identity", "Identity", CheckStatus.VERIFIED);
        documents.attach(check.id(), "application/pdf", ReportFixtures.blankPdf(3), false, false, null);
        DetailPage page = (DetailPage) assembler.assemble(ReportFixtures.report(List.of(check), null)).pages().get(1);
        assertThat(page.frames().get(0).imageSrc()).startsWith("data:image/png;base64,");
        assertThat(page.frames().get(0).note()).isEqualTo("PDF document — page 1 of 3");
    }

    @Test
    void aDamagedPdfOrPictureBecomesANoteNotAFailure() {
        Check check = ReportFixtures.check("AADHAAR", "identity", "Identity", CheckStatus.VERIFIED);
        documents.attach(check.id(), "application/pdf", "%PDF-1.4 not really".getBytes(), false, false, null);
        documents.attach(check.id(), "image/png", new byte[]{1, 2, 3}, false, false, new Crop(0, 0, 1, 1));
        var lost = documents.attach(check.id(), "image/png", ReportFixtures.png(50, 50, Color.WHITE), false, false, null);
        documents.lose(lost);
        DetailPage page = (DetailPage) assembler.assemble(ReportFixtures.report(List.of(check), null)).pages().get(1);

        assertThat(page.frames()).extracting(f -> f.note()).containsExactly("This PDF could not be shown.", "This picture could not be shown.",
                "This document could not be loaded.");
        assertThat(page.frames()).extracting(f -> f.imageSrc()).containsOnlyNulls();
    }

    // ---- extras -------------------------------------------------------------------------------------------------

    @Test
    void theWatermarkTextFallsBackToTheDefault() {
        Check check = ReportFixtures.check("AADHAAR", "identity", "Identity", CheckStatus.VERIFIED);
        ReportDocument plain = assembler.assemble(ReportFixtures.withSettings(ReportFixtures.report(List.of(check), null), 4, "NUMERIC", true, " "));
        assertThat(plain.watermark()).isTrue();
        assertThat(plain.watermarkText()).isEqualTo("NEXLYN VERIFIED");
        ReportDocument custom = assembler.assemble(ReportFixtures.withSettings(ReportFixtures.report(List.of(check), null), 4, "NUMERIC", true, "CONFIDENTIAL"));
        assertThat(custom.watermarkText()).isEqualTo("CONFIDENTIAL");
    }

    // ---- documents moved automatically (the job runner does this when a page does not fit) ----------------------------

    @Test
    void aForcedDocumentGetsAPageOfItsOwnAndNumbersFollowThePageOrder() {
        Check court = ReportFixtures.check("COURT", "court", "Court Record", CheckStatus.VERIFIED);
        var first = documents.attach(court.id(), "image/png", ReportFixtures.png(100, 80, Color.WHITE), false, false, null);
        var second = documents.attach(court.id(), "image/png", ReportFixtures.png(100, 80, Color.WHITE), false, false, null);
        var report = ReportFixtures.report(List.of(court), null);

        ReportModelAssembler.Assembly none = assembler.assemble(report, java.util.Set.of());
        assertThat(kinds(none.document())).containsExactly("cover", "detail", "services");
        assertThat(none.flowDocuments(2)).as("both documents are on the detail page").containsExactly(first, second);

        ReportModelAssembler.Assembly forced = assembler.assemble(report, java.util.Set.of(second));
        assertThat(kinds(forced.document())).containsExactly("cover", "detail", "document", "services");
        assertThat(forced.document().pages()).extracting(Page::total).containsOnly(4);
        assertThat(forced.flowDocuments(2)).containsExactly(first);
        DocumentPage moved = (DocumentPage) forced.document().pages().get(2);
        assertThat(moved.frame().header()).contains("Document 2");
        assertThat(moved.large()).as("the automatically moved document gets the larger box").isTrue();
    }

    @Test
    void aDocumentTheAnalystAlreadyMovedIsNotListedAsStayingOnThePage() {
        Check court = ReportFixtures.check("COURT", "court", "Court Record", CheckStatus.VERIFIED);
        documents.attach(court.id(), "image/png", ReportFixtures.png(100, 80, Color.WHITE), true, false, null);
        var stays = documents.attach(court.id(), "image/png", ReportFixtures.png(100, 80, Color.WHITE), false, false, null);
        ReportModelAssembler.Assembly assembly = assembler.assemble(ReportFixtures.report(List.of(court), null), java.util.Set.of());
        assertThat(assembly.flowDocuments(2)).containsExactly(stays);
        assertThat(assembly.flowDocuments(1)).as("the cover has none").isEmpty();
    }
}
