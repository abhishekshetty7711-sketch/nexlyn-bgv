package com.nexlyn.bgv.documents.internal.service;

import com.nexlyn.bgv.documents.internal.config.UploadProperties;
import com.nexlyn.bgv.documents.internal.storage.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Deletes the stored bytes of documents that were removed long enough ago (CLAUDE.md sections 11.4 and 14,
 * decision D-030). Removing a document only retires its row; the file stays for a retention period (so a
 * mistake can be undone by an administrator) and is then deleted for good. The row stays as history, marked
 * {@code purged_at}. A retention of 0 days switches purging off.
 *
 * <p>Only documents that are already retired are touched. Anything still in use, and every stored report,
 * is left alone.
 */
@Service
public class DocumentPurgeService {

    private static final Logger log = LoggerFactory.getLogger(DocumentPurgeService.class);
    private static final int BATCH = 200;

    private record Candidate(java.util.UUID id, String storageKey) {
    }

    private final JdbcTemplate jdbc;
    private final StorageService storage;
    private final UploadProperties properties;
    private final Clock clock;

    public DocumentPurgeService(JdbcTemplate jdbc, StorageService storage, UploadProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.properties = properties;
        this.clock = clock;
    }

    /** Every night at 03:30 India time. */
    @Scheduled(cron = "0 30 3 * * *", zone = "Asia/Kolkata")
    public void nightly() {
        int days = properties.retentionDaysOrDefault();
        if (days <= 0) {
            return;
        }
        int purged = purgeRetiredBefore(Instant.now(clock).minus(Duration.ofDays(days)));
        if (purged > 0) {
            log.info("Purged the stored files of {} retired documents older than {} days", purged, days);
        }
    }

    /**
     * Deletes the bytes of documents retired before {@code cutoff} (in batches, until none are left) and
     * marks them purged. A file that cannot be deleted now is left for the next night. Returns how many were purged.
     */
    public int purgeRetiredBefore(Instant cutoff) {
        int total = 0;
        while (true) {
            List<Candidate> batch = jdbc.query(
                    "SELECT id, storage_key FROM documents.documents WHERE deleted_at IS NOT NULL AND deleted_at < ? AND purged_at IS NULL "
                            + "ORDER BY deleted_at LIMIT " + BATCH,
                    (rs, i) -> new Candidate(rs.getObject("id", java.util.UUID.class), rs.getString("storage_key")),
                    java.sql.Timestamp.from(cutoff));
            if (batch.isEmpty()) {
                return total;
            }
            int purgedInBatch = 0;
            for (Candidate candidate : batch) {
                try {
                    storage.delete(candidate.storageKey());
                    jdbc.update("UPDATE documents.documents SET purged_at = ? WHERE id = ?", java.sql.Timestamp.from(Instant.now(clock)), candidate.id());
                    purgedInBatch++;
                } catch (RuntimeException e) {
                    log.warn("Could not purge a retired document file (it will be tried again): {}", e.toString());
                }
            }
            total += purgedInBatch;
            if (purgedInBatch == 0) {
                return total; // nothing could be deleted right now (store down?): stop instead of looping
            }
        }
    }
}
