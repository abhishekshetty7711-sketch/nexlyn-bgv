package com.nexlyn.bgv.documents.internal.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexlyn.bgv.documents.DocumentsIntegrationTestBase;
import com.nexlyn.bgv.documents.TestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

class DocumentApiIntegrationTest extends DocumentsIntegrationTestBase {

    UUID clientId;
    UUID analystId;
    String analyst;        // prepares assigned cases: may upload but not delete
    String analystDeleter; // the same plus DOCUMENT_DELETE
    String caseId;
    String checkId;

    @BeforeEach
    void fixtures() throws Exception {
        clientId = newClient();
        analystId = newAdmin("analyst@example.com", "Ann Analyst");
        String[] base = {"CASE_CREATE", "CASE_READ_ASSIGNED", "CASE_UPDATE", "CHECK_UPDATE", "DOCUMENT_UPLOAD"};
        analyst = tokenFor(analystId, "analyst@example.com", base);
        String[] withDelete = java.util.Arrays.copyOf(base, base.length + 1);
        withDelete[base.length] = "DOCUMENT_DELETE";
        analystDeleter = tokenFor(analystId, "analyst@example.com", withDelete);
        caseId = newCase(clientId, analyst).get("id").asText();
        checkId = newCheck(caseId, "EDUCATION", analyst).get("id").asText();
    }

    // ---- helpers ------------------------------------------------------------------------------------------

    private String docs() {
        return "/api/checks/" + checkId + "/documents";
    }

    private JsonNode uploadOk(String filename, byte[] bytes, String... params) throws Exception {
        MvcResult result = upload(docs(), analyst, filename, bytes, params);
        assertThat(status(result)).as(result.getResponse().getContentAsString()).isEqualTo(201);
        return body(result);
    }

    private JsonNode candidateOf(String token) throws Exception {
        return body(send(get("/api/cases/" + caseId), token, null)).get("candidate");
    }

    private JsonNode validation() throws Exception {
        return body(send(get("/api/cases/" + caseId + "/validation"), analyst, null));
    }

    private boolean hasWarning(String containing) throws Exception {
        for (JsonNode w : validation().get("warnings")) {
            if (w.get("message").asText().contains(containing)) {
                return true;
            }
        }
        return false;
    }

    private Integer deletedRows(String documentId) {
        return jdbc.queryForObject("SELECT count(*)::int FROM documents.documents WHERE id = ?::uuid AND deleted_at IS NOT NULL", Integer.class, documentId);
    }

    // ---- uploading a supporting document ------------------------------------------------------------------

    @Test
    void storesAPictureCleanedAndDescribed() throws Exception {
        JsonNode doc = uploadOk("Asha Rao - degree.jpg", TestImages.jpeg(300, 200));

        assertThat(doc.get("kind").asText()).isEqualTo("CHECK_DOC");
        assertThat(doc.get("mimeType").asText()).isEqualTo("image/jpeg");
        assertThat(doc.get("width").asInt()).isEqualTo(300);
        assertThat(doc.get("height").asInt()).isEqualTo(200);
        assertThat(doc.get("quality").asText()).isEqualTo("LOW");
        assertThat(doc.get("displayLabel").asText()).isEqualTo("Original Document");
        assertThat(doc.get("originalFilename").asText()).isEqualTo("Asha Rao - degree.jpg");

        // The file is in the store under a key that does not contain the file name, and is not the raw upload.
        String key = jdbc.queryForObject("SELECT storage_key FROM documents.documents WHERE id = ?::uuid", String.class, doc.get("id").asText());
        assertThat(key).startsWith("cases/" + caseId + "/").doesNotContain("degree").doesNotContain("Asha");
        assertThat(storage.objects).containsKey(key);
        assertThat(TestImages.read(storage.objects.get(key)).getWidth()).isEqualTo(300);
        String sha = jdbc.queryForObject("SELECT sha256 FROM documents.documents WHERE id = ?::uuid", String.class, doc.get("id").asText());
        assertThat(sha).hasSize(64);
    }

    @Test
    void numbersTheDocumentsOriginalThenAdditional() throws Exception {
        uploadOk("a.jpg", TestImages.jpeg(50, 50));
        uploadOk("b.png", TestImages.png(50, 50));
        uploadOk("c.pdf", TestImages.pdf());
        JsonNode list = body(send(get(docs()), analyst, null));
        List<String> labels = new ArrayList<>();
        list.forEach(d -> labels.add(d.get("displayLabel").asText()));
        assertThat(labels).containsExactly("Original Document", "Additional Document 1", "Additional Document 2");
    }

    @Test
    void acceptsAPdfAsASupportingDocumentButNotAsAPicture() throws Exception {
        JsonNode pdf = uploadOk("letter.pdf", TestImages.pdf());
        assertThat(pdf.get("mimeType").asText()).isEqualTo("application/pdf");
        assertThat(pdf.get("quality").isNull()).isTrue();
        assertThat(pdf.get("width").isNull()).isTrue();

        MvcResult asFree = upload(docs(), analyst, "letter.pdf", TestImages.pdf(), "kind", "FREE_IMAGE");
        assertThat(status(asFree)).isEqualTo(415);
        assertThat(code(asFree)).isEqualTo("UNSUPPORTED_FILE");
        MvcResult asPhoto = upload("/api/cases/" + caseId + "/candidate/photo", analyst, "letter.pdf", TestImages.pdf());
        assertThat(status(asPhoto)).isEqualTo(415);
    }

    @Test
    void goesByTheBytesNotTheNameOrTheClaimedType() throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes(StandardCharsets.UTF_8);
        MvcResult fake = upload(docs(), analyst, "nice.jpg", html);
        assertThat(status(fake)).isEqualTo(415);
        assertThat(code(fake)).isEqualTo("UNSUPPORTED_FILE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents.documents", Integer.class)).isZero();
        assertThat(storage.objects).isEmpty();

        // A real picture is accepted whatever it is called.
        JsonNode ok = uploadOk("report.txt", TestImages.png(20, 20));
        assertThat(ok.get("mimeType").asText()).isEqualTo("image/png");
    }

    @Test
    void refusesEmptyOversizedAndOverPixelledFiles() throws Exception {
        assertThat(status(upload(docs(), analyst, "x.jpg", new byte[0]))).isEqualTo(400);

        byte[] tooBig = new byte[250_000]; // limit in these tests is 200,000 bytes
        tooBig[0] = (byte) 0xFF;
        tooBig[1] = (byte) 0xD8;
        tooBig[2] = (byte) 0xFF;
        MvcResult big = upload(docs(), analyst, "x.jpg", tooBig);
        assertThat(status(big)).isEqualTo(413);
        assertThat(code(big)).isEqualTo("FILE_TOO_LARGE");

        MvcResult pixels = upload(docs(), analyst, "x.png", TestImages.png(1200, 1000)); // limit 1 million pixels
        assertThat(status(pixels)).isEqualTo(415);
        assertThat(body(pixels).get("message").asText()).contains("too many pixels");
        assertThat(storage.objects).isEmpty();
    }

    @Test
    void turnsASidewaysPhotoUpright() throws Exception {
        JsonNode doc = uploadOk("phone.jpg", TestImages.withExifOrientation(TestImages.jpeg(60, 20), 6));
        assertThat(doc.get("width").asInt()).isEqualTo(20);
        assertThat(doc.get("height").asInt()).isEqualTo(60);
    }

    @Test
    void limitsHowManyFilesACheckCanHold() throws Exception {
        byte[] small = TestImages.jpeg(16, 16);
        for (int i = 0; i < 30; i++) {
            uploadOk("f" + i + ".jpg", small);
        }
        MvcResult extra = upload(docs(), analyst, "one-more.jpg", small);
        assertThat(status(extra)).isEqualTo(409);
    }

    @Test
    void cleansTheFileNameForDisplay() throws Exception {
        JsonNode doc = uploadOk("C:\\Users\\me\\Desktop\\..\\scan\u0007.jpg", TestImages.jpeg(20, 20));
        assertThat(doc.get("originalFilename").asText()).isEqualTo("scan.jpg");
    }

    // ---- who may do what -----------------------------------------------------------------------------------

    @Test
    void needsSigningInAndThePermission() throws Exception {
        assertThat(status(upload(docs(), null, "a.jpg", TestImages.jpeg(20, 20)))).isEqualTo(401);
        String reader = tokenFor(analystId, "analyst@example.com", "CASE_READ_ASSIGNED");
        assertThat(status(upload(docs(), reader, "a.jpg", TestImages.jpeg(20, 20)))).isEqualTo(403);
        assertThat(status(send(get(docs()), null, null))).isEqualTo(401);
        String noRead = tokenFor(analystId, "analyst@example.com", "DOCUMENT_UPLOAD");
        assertThat(status(send(get(docs()), noRead, null))).isEqualTo(403);
    }

    @Test
    void anAnalystCannotReachAnUnassignedCase() throws Exception {
        JsonNode doc = uploadOk("a.jpg", TestImages.jpeg(20, 20));
        UUID otherId = newAdmin("other@example.com", "Otto Other");
        String other = tokenFor(otherId, "other@example.com", "CASE_READ_ASSIGNED", "DOCUMENT_UPLOAD", "DOCUMENT_DELETE");

        assertThat(status(upload(docs(), other, "b.jpg", TestImages.jpeg(20, 20)))).isEqualTo(403);
        assertThat(status(send(get(docs()), other, null))).isEqualTo(403);
        assertThat(status(send(get("/api/documents/" + doc.get("id").asText() + "/content"), other, null))).isEqualTo(403);
        assertThat(status(send(delete("/api/documents/" + doc.get("id").asText()), other, null))).isEqualTo(403);
        assertThat(status(upload("/api/cases/" + caseId + "/candidate/photo", other, "p.jpg", TestImages.jpeg(20, 20)))).isEqualTo(403);

        // Someone who may read every case can look but not change (no upload permission).
        String auditor = tokenFor(otherId, "other@example.com", "CASE_READ_ALL");
        assertThat(status(send(get("/api/documents/" + doc.get("id").asText() + "/content"), auditor, null))).isEqualTo(200);
        assertThat(status(upload(docs(), auditor, "b.jpg", TestImages.jpeg(20, 20)))).isEqualTo(403);
    }

    @Test
    void unknownIdsAreNotFound() throws Exception {
        UUID nothing = UUID.randomUUID();
        assertThat(status(upload("/api/checks/" + nothing + "/documents", analyst, "a.jpg", TestImages.jpeg(20, 20)))).isEqualTo(404);
        assertThat(status(send(get("/api/documents/" + nothing + "/content"), analyst, null))).isEqualTo(404);
        assertThat(status(send(get("/api/checks/" + nothing + "/documents"), analyst, null))).isEqualTo(404);
    }

    @Test
    void aBadKindIsAValidationError() throws Exception {
        MvcResult result = upload(docs(), analyst, "a.jpg", TestImages.jpeg(20, 20), "kind", "PHOTO");
        assertThat(status(result)).isEqualTo(400);
        assertThat(status(upload(docs(), analyst, "a.jpg", TestImages.jpeg(20, 20), "kind", "NONSENSE"))).isEqualTo(400);
    }

    // ---- reading the file ---------------------------------------------------------------------------------

    @Test
    void servesPicturesInlineAndPdfsAsDownloadsWithSafeHeaders() throws Exception {
        JsonNode picture = uploadOk("scan.png", TestImages.png(30, 30));
        MvcResult image = send(get("/api/documents/" + picture.get("id").asText() + "/content"), analyst, null);
        assertThat(status(image)).isEqualTo(200);
        assertThat(image.getResponse().getContentType()).isEqualTo("image/png");
        assertThat(image.getResponse().getHeader("Content-Disposition")).startsWith("inline");
        assertThat(image.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(image.getResponse().getHeader("Content-Security-Policy")).contains("sandbox");
        assertThat(image.getResponse().getHeader("Cache-Control")).contains("no-store").contains("private");
        assertThat(TestImages.read(image.getResponse().getContentAsByteArray()).getWidth()).isEqualTo(30);

        JsonNode pdf = uploadOk("letter.pdf", TestImages.pdf());
        MvcResult download = send(get("/api/documents/" + pdf.get("id").asText() + "/content"), analyst, null);
        assertThat(download.getResponse().getContentType()).isEqualTo("application/pdf");
        assertThat(download.getResponse().getHeader("Content-Disposition")).startsWith("attachment").contains("letter.pdf");
    }

    @Test
    void writesAnAuditTrailWithoutFileNames() throws Exception {
        JsonNode doc = uploadOk("Asha-Rao-marksheet.jpg", TestImages.jpeg(30, 30));
        send(get("/api/documents/" + doc.get("id").asText() + "/content"), analyst, null);
        send(delete("/api/documents/" + doc.get("id").asText()), analystDeleter, null);

        assertThat(auditActionsSinceStart()).contains("DOCUMENT_UPLOADED", "DOCUMENT_VIEWED", "DOCUMENT_DELETED");
        Integer leaked = jdbc.queryForObject("SELECT count(*)::int FROM auth.audit_log WHERE at >= ? AND (after::text ILIKE '%Asha%' OR before::text ILIKE '%Asha%')",
                Integer.class, java.sql.Timestamp.from(testStart));
        assertThat(leaked).as("no file name in the audit log").isZero();
    }

    // ---- describing, ordering, deleting -------------------------------------------------------------------

    @Test
    void savesALabelACropAndPagePreferences() throws Exception {
        JsonNode doc = uploadOk("a.jpg", TestImages.jpeg(100, 100));
        String id = doc.get("id").asText();
        MvcResult saved = send(put("/api/documents/" + id), analyst, obj(
                "label", "  Degree certificate ", "moveToNextPage", true, "useLargerBox", true,
                "crop", obj("x", 0.1, "y", 0.2, "width", 0.5, "height", 0.6), "version", doc.get("version").asLong()));
        assertThat(status(saved)).as(saved.getResponse().getContentAsString()).isEqualTo(200);
        JsonNode view = body(saved);
        assertThat(view.get("label").asText()).isEqualTo("Degree certificate");
        assertThat(view.get("displayLabel").asText()).isEqualTo("Degree certificate");
        assertThat(view.get("moveToNextPage").asBoolean()).isTrue();
        assertThat(view.get("useLargerBox").asBoolean()).isTrue();
        assertThat(view.get("crop").get("width").asDouble()).isEqualTo(0.5);
        assertThat(view.get("version").asLong()).isGreaterThan(doc.get("version").asLong());

        // A blank label goes back to the numbered name; no crop clears it.
        MvcResult cleared = send(put("/api/documents/" + id), analyst, obj("label", " ", "moveToNextPage", false, "useLargerBox", false, "crop", null));
        assertThat(body(cleared).get("displayLabel").asText()).isEqualTo("Original Document");
        assertThat(body(cleared).get("crop").isNull()).isTrue();
    }

    @Test
    void rejectsBadCropsAndOptionsThatDoNotFit() throws Exception {
        JsonNode picture = uploadOk("a.jpg", TestImages.jpeg(100, 100));
        JsonNode pdf = uploadOk("b.pdf", TestImages.pdf());
        String id = picture.get("id").asText();

        MvcResult outside = send(put("/api/documents/" + id), analyst, obj("moveToNextPage", false, "useLargerBox", false, "crop", obj("x", 0.6, "y", 0, "width", 0.6, "height", 1)));
        assertThat(status(outside)).isEqualTo(400);
        MvcResult label = send(put("/api/documents/" + id), analyst, obj("label", "x".repeat(101), "moveToNextPage", false, "useLargerBox", false));
        assertThat(status(label)).isEqualTo(400);
        MvcResult cropPdf = send(put("/api/documents/" + pdf.get("id").asText()), analyst,
                obj("moveToNextPage", false, "useLargerBox", false, "crop", obj("x", 0, "y", 0, "width", 1, "height", 1)));
        assertThat(status(cropPdf)).isEqualTo(400);
        MvcResult largerPdf = send(put("/api/documents/" + pdf.get("id").asText()), analyst, obj("moveToNextPage", false, "useLargerBox", true));
        assertThat(status(largerPdf)).isEqualTo(400);
        // A PDF can still go to the next page.
        assertThat(status(send(put("/api/documents/" + pdf.get("id").asText()), analyst, obj("moveToNextPage", true, "useLargerBox", false)))).isEqualTo(200);
    }

    @Test
    void notesWhenSomeoneElseChangedTheDocumentFirst() throws Exception {
        JsonNode doc = uploadOk("a.jpg", TestImages.jpeg(50, 50));
        String id = doc.get("id").asText();
        assertThat(status(send(put("/api/documents/" + id), analyst, obj("label", "First", "moveToNextPage", false, "useLargerBox", false, "version", 0)))).isEqualTo(200);
        MvcResult stale = send(put("/api/documents/" + id), analyst, obj("label", "Second", "moveToNextPage", false, "useLargerBox", false, "version", 0));
        assertThat(status(stale)).isEqualTo(409);
    }

    @Test
    void reordersAndRenumbers() throws Exception {
        String first = uploadOk("a.jpg", TestImages.jpeg(30, 30)).get("id").asText();
        String second = uploadOk("b.jpg", TestImages.jpeg(30, 30)).get("id").asText();
        String third = uploadOk("c.jpg", TestImages.jpeg(30, 30)).get("id").asText();

        MvcResult reordered = send(patch(docs() + "/order"), analyst, obj("ids", List.of(third, first, second)));
        assertThat(status(reordered)).as(reordered.getResponse().getContentAsString()).isEqualTo(200);
        JsonNode list = body(reordered);
        assertThat(list.get(0).get("id").asText()).isEqualTo(third);
        assertThat(list.get(0).get("displayLabel").asText()).isEqualTo("Original Document");
        assertThat(list.get(1).get("displayLabel").asText()).isEqualTo("Additional Document 1");
        assertThat(list.get(2).get("id").asText()).isEqualTo(second);

        assertThat(status(send(patch(docs() + "/order"), analyst, obj("ids", List.of(first, second))))).as("missing one").isEqualTo(400);
        assertThat(status(send(patch(docs() + "/order"), analyst, obj("ids", List.of(first, second, UUID.randomUUID().toString()))))).isEqualTo(400);
    }

    @Test
    void deletingNeedsItsOwnPermissionAndKeepsTheStoredBytes() throws Exception {
        JsonNode doc = uploadOk("a.jpg", TestImages.jpeg(30, 30));
        String id = doc.get("id").asText();
        assertThat(status(send(delete("/api/documents/" + id), analyst, null))).isEqualTo(403);

        assertThat(status(send(delete("/api/documents/" + id), analystDeleter, null))).isEqualTo(204);
        assertThat(body(send(get(docs()), analyst, null))).isEmpty();
        assertThat(deletedRows(id)).isEqualTo(1);
        assertThat(storage.objects).as("the file itself is kept (retention purge comes later)").hasSize(1);
        assertThat(status(send(get("/api/documents/" + id + "/content"), analyst, null))).isEqualTo(404);
        assertThat(status(send(delete("/api/documents/" + id), analystDeleter, null))).isEqualTo(404);
    }

    // ---- a locked case -------------------------------------------------------------------------------------

    @Test
    void nothingChangesOnceTheCaseIsLockedButReadingStillWorks() throws Exception {
        JsonNode doc = uploadOk("a.jpg", TestImages.jpeg(30, 30));
        String id = doc.get("id").asText();
        jdbc.update("UPDATE cases.cases SET lifecycle = 'IN_REVIEW' WHERE id = ?::uuid", caseId);

        assertThat(status(upload(docs(), analyst, "b.jpg", TestImages.jpeg(30, 30)))).isEqualTo(409);
        assertThat(status(send(put("/api/documents/" + id), analyst, obj("label", "x", "moveToNextPage", false, "useLargerBox", false)))).isEqualTo(409);
        assertThat(status(send(delete("/api/documents/" + id), analystDeleter, null))).isEqualTo(409);
        assertThat(status(send(patch(docs() + "/order"), analyst, obj("ids", List.of(id))))).isEqualTo(409);
        assertThat(status(upload("/api/cases/" + caseId + "/candidate/photo", analyst, "p.jpg", TestImages.jpeg(30, 30)))).isEqualTo(409);

        assertThat(status(send(get("/api/documents/" + id + "/content"), analyst, null))).isEqualTo(200);
        assertThat(status(send(get(docs()), analyst, null))).isEqualTo(200);
    }

    // ---- the candidate photo -------------------------------------------------------------------------------

    @Test
    void thePhotoIsSetReplacedAndRemoved() throws Exception {
        assertThat(candidateOf(analyst).get("hasPhoto").asBoolean()).isFalse();
        assertThat(hasWarning("photo is missing")).isTrue();

        String photoUrl = "/api/cases/" + caseId + "/candidate/photo";
        MvcResult first = upload(photoUrl, analyst, "me.jpg", TestImages.jpeg(120, 160));
        assertThat(status(first)).as(first.getResponse().getContentAsString()).isEqualTo(201);
        String firstId = body(first).get("id").asText();
        assertThat(body(first).get("kind").asText()).isEqualTo("PHOTO");
        assertThat(body(first).get("checkId").isNull()).isTrue();
        assertThat(candidateOf(analyst).get("hasPhoto").asBoolean()).isTrue();
        assertThat(candidateOf(analyst).get("photoDocumentId").asText()).isEqualTo(firstId);
        assertThat(hasWarning("photo is missing")).isFalse();

        String secondId = body(upload(photoUrl, analyst, "me2.png", TestImages.png(80, 80))).get("id").asText();
        assertThat(candidateOf(analyst).get("photoDocumentId").asText()).as("the new photo replaces the old one").isEqualTo(secondId);
        assertThat(deletedRows(firstId)).as("the old photo is retired").isEqualTo(1);
        assertThat(deletedRows(secondId)).isZero();
        assertThat(status(send(get("/api/documents/" + secondId + "/content"), analyst, null))).isEqualTo(200);

        assertThat(status(send(delete(photoUrl), analyst, null))).as("removing needs DOCUMENT_DELETE").isEqualTo(403);
        assertThat(status(send(delete(photoUrl), analystDeleter, null))).isEqualTo(204);
        assertThat(candidateOf(analyst).get("hasPhoto").asBoolean()).isFalse();
        assertThat(deletedRows(secondId)).isEqualTo(1);
        assertThat(hasWarning("photo is missing")).isTrue();
    }

    @Test
    void uploadingAPhotoDoesNotMakeAnOpenCandidateFormStale() throws Exception {
        long before = body(send(get("/api/cases/" + caseId), analyst, null)).get("version").asLong();
        upload("/api/cases/" + caseId + "/candidate/photo", analyst, "me.jpg", TestImages.jpeg(60, 60));
        long after = body(send(get("/api/cases/" + caseId), analyst, null)).get("version").asLong();
        assertThat(after).isEqualTo(before);
    }

    // ---- what the cases module learns -----------------------------------------------------------------------

    @Test
    void validationWarnsUntilEachCheckHasADocument() throws Exception {
        assertThat(hasWarning("no supporting document is attached")).isTrue();
        JsonNode doc = uploadOk("a.jpg", TestImages.jpeg(30, 30));
        assertThat(hasWarning("no supporting document is attached")).isFalse();
        send(delete("/api/documents/" + doc.get("id").asText()), analystDeleter, null);
        assertThat(hasWarning("no supporting document is attached")).isTrue();
    }

    @Test
    void deletingACheckRetiresItsDocuments() throws Exception {
        String a = uploadOk("a.jpg", TestImages.jpeg(30, 30)).get("id").asText();
        String b = uploadOk("b.jpg", TestImages.jpeg(30, 30), "kind", "FREE_IMAGE").get("id").asText();
        assertThat(status(send(delete("/api/cases/" + caseId + "/checks/" + checkId), analyst, null))).isEqualTo(204);
        assertThat(deletedRows(a)).isEqualTo(1);
        assertThat(deletedRows(b)).isEqualTo(1);
        assertThat(status(send(get(docs()), analyst, null))).as("the check is gone").isEqualTo(404);
    }

    // ---- image blocks on a check ----------------------------------------------------------------------------

    @Test
    void anImageBlockUsesAnUploadedPictureOfTheSameCheck() throws Exception {
        String picture = uploadOk("chart.png", TestImages.png(40, 40), "kind", "FREE_IMAGE").get("id").asText();
        String blocks = "/api/cases/" + caseId + "/checks/" + checkId + "/free-sections";

        MvcResult added = send(post(blocks), analyst, obj("kind", "IMAGE", "documentId", picture));
        assertThat(status(added)).as(added.getResponse().getContentAsString()).isEqualTo(201);
        JsonNode section = body(added).get("freeSections").get(0);
        assertThat(section.get("kind").asText()).isEqualTo("IMAGE");
        assertThat(section.get("documentId").asText()).isEqualTo(picture);

        assertThat(status(send(post(blocks), analyst, obj("kind", "IMAGE", "documentId", picture)))).as("already used").isEqualTo(400);
        assertThat(status(send(post(blocks), analyst, obj("kind", "IMAGE")))).as("no picture").isEqualTo(400);
        assertThat(status(send(post(blocks), analyst, obj("kind", "IMAGE", "documentId", UUID.randomUUID().toString())))).isEqualTo(400);

        // A supporting document, or a picture of another check, is not accepted.
        String support = uploadOk("s.png", TestImages.png(20, 20)).get("id").asText();
        assertThat(status(send(post(blocks), analyst, obj("kind", "IMAGE", "documentId", support)))).isEqualTo(400);
        String otherCheck = newCheck(caseId, "PAN", analyst).get("id").asText();
        String otherPicture = body(upload("/api/checks/" + otherCheck + "/documents", analyst, "o.png", TestImages.png(20, 20), "kind", "FREE_IMAGE")).get("id").asText();
        assertThat(status(send(post(blocks), analyst, obj("kind", "IMAGE", "documentId", otherPicture)))).isEqualTo(400);

        // The picture cannot be deleted on its own while it is a block; removing the block retires it.
        assertThat(status(send(delete("/api/documents/" + picture), analystDeleter, null))).isEqualTo(409);
        String sectionId = section.get("id").asText();
        assertThat(status(send(delete(blocks + "/" + sectionId), analyst, null))).isEqualTo(200);
        assertThat(deletedRows(picture)).isEqualTo(1);
    }

    @Test
    void pictureBlocksDoNotCountAsSupportingDocuments() throws Exception {
        uploadOk("chart.png", TestImages.png(40, 40), "kind", "FREE_IMAGE");
        assertThat(hasWarning("no supporting document is attached")).isTrue();
        JsonNode list = body(send(get(docs()), analyst, null));
        assertThat(list).hasSize(1);
        assertThat(list.get(0).get("kind").asText()).isEqualTo("FREE_IMAGE");
        assertThat(list.get(0).get("displayLabel").asText()).isEqualTo("Image");
        // ...and reordering only involves supporting documents.
        assertThat(status(send(patch(docs() + "/order"), analyst, obj("ids", List.of())))).isEqualTo(200);
    }
}
