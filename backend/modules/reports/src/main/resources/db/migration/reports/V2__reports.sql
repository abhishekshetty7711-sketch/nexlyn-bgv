-- Report jobs and versions (CLAUDE.md section 10, schema "reports"). case_id and generated_by are plain
-- ids: no foreign keys to other modules (CLAUDE.md 4.2 rule 4). The PDF itself lives in the private file
-- store under pdf_storage_key; this table holds what is known about it.

CREATE TABLE report_jobs (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id      UUID        NOT NULL,
    status       VARCHAR(10) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'DONE', 'FAILED')),
    version      INT,                                   -- the version this job produced (when DONE)
    error        TEXT,                                  -- plain-language reason (when FAILED)
    warnings     JSONB,                                 -- e.g. pages whose content did not fit
    requested_by UUID        NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at   TIMESTAMPTZ,
    finished_at  TIMESTAMPTZ
);
CREATE INDEX ix_report_jobs_case ON report_jobs (case_id, requested_at DESC);

CREATE TABLE report_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id         UUID         NOT NULL,
    version         INT          NOT NULL,
    kind            VARCHAR(5)   NOT NULL CHECK (kind IN ('DRAFT', 'FINAL')),
    pdf_storage_key VARCHAR(300) NOT NULL UNIQUE,
    sha256          CHAR(64)     NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    page_count      INT          NOT NULL,
    encrypted       BOOLEAN      NOT NULL DEFAULT FALSE,
    generated_by    UUID         NOT NULL,
    generated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    finalized_by    UUID,
    finalized_at    TIMESTAMPTZ,
    warnings        JSONB,
    snapshot        JSONB        NOT NULL,              -- the case data the report was made from (numbers masked)
    UNIQUE (case_id, version)
);
