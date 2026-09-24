-- Retired documents keep their row forever (history), but their stored bytes are deleted after a retention
-- period. purged_at says when that happened (see DocumentPurgeService).
ALTER TABLE documents ADD COLUMN purged_at TIMESTAMPTZ;
CREATE INDEX ix_documents_to_purge ON documents (deleted_at) WHERE deleted_at IS NOT NULL AND purged_at IS NULL;
