-- Uploaded files (CLAUDE.md section 10, schema "documents"). The bytes live in object storage under
-- storage_key; this table holds what is known about them. No foreign keys to other modules: case_id
-- and check_id are plain ids (CLAUDE.md 4.2 rule 4). Rows are never hard-deleted (deleted_at).

CREATE TABLE documents (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id           UUID         NOT NULL,
    check_id          UUID,                                     -- null for the candidate photo
    kind              VARCHAR(12)  NOT NULL CHECK (kind IN ('PHOTO', 'CHECK_DOC', 'FREE_IMAGE')),
    label             VARCHAR(100),                             -- custom label; null = "Original / Additional Document N"
    storage_key       VARCHAR(300) NOT NULL UNIQUE,             -- never derived from the file name
    original_filename VARCHAR(255),                             -- display only
    mime_type         VARCHAR(100) NOT NULL,                    -- detected from the bytes, not from the upload
    size_bytes        BIGINT       NOT NULL,
    sha256            CHAR(64)     NOT NULL,
    width             INT,
    height            INT,
    quality           VARCHAR(6) CHECK (quality IN ('HIGH', 'MEDIUM', 'LOW')),   -- images only
    move_to_next_page BOOLEAN      NOT NULL DEFAULT FALSE,
    use_larger_box    BOOLEAN      NOT NULL DEFAULT FALSE,
    crop              JSONB,                                    -- {x, y, width, height} as fractions of the image
    sort_order        INT          NOT NULL DEFAULT 0,
    uploaded_by       UUID         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ,
    version           BIGINT       NOT NULL DEFAULT 0,
    CHECK (kind = 'PHOTO' OR check_id IS NOT NULL)
);
CREATE INDEX ix_documents_case ON documents (case_id) WHERE deleted_at IS NULL;
CREATE INDEX ix_documents_check ON documents (check_id, sort_order) WHERE deleted_at IS NULL;
