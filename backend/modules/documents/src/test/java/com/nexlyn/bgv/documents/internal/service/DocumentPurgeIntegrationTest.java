package com.nexlyn.bgv.documents.internal.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nexlyn.bgv.documents.DocumentsIntegrationTestBase;
import com.nexlyn.bgv.documents.TestImages;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Retired documents lose their stored bytes after the retention period, and only then. */
class DocumentPurgeIntegrationTest extends DocumentsIntegrationTestBase {

    @Autowired DocumentPurgeService purge;

    String analyst;
    String checkId;

    @BeforeEach
    void fixtures() throws Exception {
        UUID clientId = newClient();
        UUID analystId = newAdmin("analyst@example.com", "Ann Analyst");
        analyst = tokenFor(analystId, "analyst@example.com", "CASE_CREATE", "CASE_READ_ASSIGNED", "CASE_UPDATE", "CHECK_UPDATE", "DOCUMENT_UPLOAD", "DOCUMENT_DELETE");
        String caseId = newCase(clientId, analyst).get("id").asText();
        checkId = newCheck(caseId, "EDUCATION", analyst).get("id").asText();
    }

    private String uploadAndRemove() throws Exception {
        JsonNode doc = body(upload("/api/checks/" + checkId + "/documents", analyst, "a.jpg", TestImages.jpeg(30, 30)));
        String id = doc.get("id").asText();
        assertThat(status(send(delete("/api/documents/" + id), analyst, null))).isEqualTo(204);
        return id;
    }

    private void retiredDaysAgo(String id, int days) {
        jdbc.update("UPDATE documents.documents SET deleted_at = ? WHERE id = ?::uuid", java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(days))), id);
    }

    private String key(String id) {
        return jdbc.queryForObject("SELECT storage_key FROM documents.documents WHERE id = ?::uuid", String.class, id);
    }

    private boolean purged(String id) {
        return jdbc.queryForObject("SELECT purged_at IS NOT NULL FROM documents.documents WHERE id = ?::uuid", Boolean.class, id);
    }

    @Test
    void deletesTheBytesOfDocumentsRetiredBeforeTheCutoffAndKeepsTheRow() throws Exception {
        String old = uploadAndRemove();
        retiredDaysAgo(old, 120);
        String oldKey = key(old);
        assertThat(storage.objects).containsKey(oldKey);

        int count = purge.purgeRetiredBefore(Instant.now().minus(Duration.ofDays(90)));

        assertThat(count).isEqualTo(1);
        assertThat(storage.objects).doesNotContainKey(oldKey);
        assertThat(purged(old)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM documents.documents WHERE id = ?::uuid", Integer.class, old)).as("the history row stays").isEqualTo(1);
    }

    @Test
    void leavesRecentlyRetiredAndStillUsedDocumentsAlone() throws Exception {
        String recent = uploadAndRemove();
        retiredDaysAgo(recent, 10);
        JsonNode inUse = body(upload("/api/checks/" + checkId + "/documents", analyst, "b.jpg", TestImages.jpeg(30, 30)));
        String inUseId = inUse.get("id").asText();

        assertThat(purge.purgeRetiredBefore(Instant.now().minus(Duration.ofDays(90)))).isZero();

        assertThat(storage.objects).containsKey(key(recent));
        assertThat(storage.objects).containsKey(key(inUseId));
        assertThat(purged(recent)).isFalse();
        assertThat(status(send(get("/api/documents/" + inUseId + "/content"), analyst, null))).isEqualTo(200);
    }

    @Test
    void doesNotPurgeTwiceAndHandlesManyInBatches() throws Exception {
        String first = uploadAndRemove();
        retiredDaysAgo(first, 200);
        assertThat(purge.purgeRetiredBefore(Instant.now().minus(Duration.ofDays(90)))).isEqualTo(1);
        assertThat(purge.purgeRetiredBefore(Instant.now().minus(Duration.ofDays(90)))).as("already purged").isZero();

        // more than one batch (200 per batch): insert plain retired rows directly
        for (int i = 0; i < 230; i++) {
            UUID id = UUID.randomUUID();
            jdbc.update("INSERT INTO documents.documents (id, case_id, check_id, kind, storage_key, mime_type, size_bytes, sha256, uploaded_by, deleted_at) "
                            + "VALUES (?, ?, ?, 'CHECK_DOC', ?, 'image/png', 1, ?, ?, ?)",
                    id, UUID.randomUUID(), UUID.fromString(checkId), "cases/x/" + id, "0".repeat(64), UUID.randomUUID(),
                    java.sql.Timestamp.from(Instant.now().minus(Duration.ofDays(100))));
            storage.put("cases/x/" + id, new byte[]{1}, "image/png");
        }
        assertThat(purge.purgeRetiredBefore(Instant.now().minus(Duration.ofDays(90)))).isEqualTo(230);
        assertThat(storage.objects.keySet()).noneMatch(k -> k.startsWith("cases/x/"));
    }

    @Test
    void aFileThatCannotBeDeletedIsTriedAgainNextTimeAndNothingLoopsForever() throws Exception {
        String id = uploadAndRemove();
        retiredDaysAgo(id, 120);
        // a store that refuses to delete
        var refusing = new com.nexlyn.bgv.documents.internal.storage.StorageService() {
            @Override public void put(String key, byte[] content, String contentType) { storage.put(key, content, contentType); }
            @Override public byte[] get(String key) { return storage.get(key); }
            @Override public void delete(String key) { throw new IllegalStateException("store is down"); }
        };
        var failing = new DocumentPurgeService(jdbc, refusing, new com.nexlyn.bgv.documents.internal.config.UploadProperties(null, null, null), java.time.Clock.systemUTC());
        assertThat(failing.purgeRetiredBefore(Instant.now().minus(Duration.ofDays(90)))).isZero();
        assertThat(purged(id)).isFalse();
        assertThat(storage.objects).containsKey(key(id));

        assertThat(purge.purgeRetiredBefore(Instant.now().minus(Duration.ofDays(90)))).isEqualTo(1);
    }

    @Test
    void theRetentionSettingDefaultsToNinetyDaysAndZeroMeansNever() {
        var defaults = new com.nexlyn.bgv.documents.internal.config.UploadProperties(null, null, null);
        assertThat(defaults.retentionDaysOrDefault()).isEqualTo(90);
        assertThat(new com.nexlyn.bgv.documents.internal.config.UploadProperties(null, null, 0).retentionDaysOrDefault()).isZero();
        assertThat(new com.nexlyn.bgv.documents.internal.config.UploadProperties(null, null, -5).retentionDaysOrDefault()).isZero();
        assertThat(new com.nexlyn.bgv.documents.internal.config.UploadProperties(null, null, 30).retentionDaysOrDefault()).isEqualTo(30);
    }
}
