-- Verification checks and their fields, details and free sections (CLAUDE.md §10 "schema cases").
-- The type-specific field lists live in YAML (check-types/*.yml), not here, so adding a field to a
-- check type never needs a migration.

CREATE TABLE verification_checks (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id              UUID         NOT NULL REFERENCES cases (id),
    type                 VARCHAR(40)  NOT NULL,                -- a check type code such as AADHAAR
    title                VARCHAR(200) NOT NULL,
    summary_description  TEXT,                                 -- the short line on page 1
    this_card_verifies   TEXT,                                 -- detail page; falls back to the document type
    status               VARCHAR(20)  NOT NULL DEFAULT 'PENDING'
                         CHECK (status IN ('VERIFIED', 'DISCREPANCY', 'UNABLE_TO_VERIFY', 'CLOSED', 'PENDING', 'IN_PROGRESS')),
    verification_type    VARCHAR(50)  NOT NULL DEFAULT 'Standard',
    requested_date       DATE,
    completed_date       DATE,
    dates_manual         BOOLEAN      NOT NULL DEFAULT FALSE,  -- true = this check's dates no longer follow the first check
    remarks              TEXT,
    has_attestation      BOOLEAN      NOT NULL DEFAULT FALSE,
    bar_council_no       VARCHAR(50),
    disclaimer           TEXT,
    sort_order           INT          NOT NULL,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by           UUID,
    updated_by           UUID,
    version              BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX ix_checks_case ON verification_checks (case_id, sort_order);

CREATE TABLE check_fields (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    check_id        UUID         NOT NULL REFERENCES verification_checks (id),
    field_key       VARCHAR(60)  NOT NULL,
    label           VARCHAR(200) NOT NULL,                     -- the label as it was when the check was made
    value           TEXT,                                      -- plain value; for sensitive fields the MASKED form
    value_encrypted TEXT,                                      -- sensitive fields only: AES-256-GCM
    value_last4     VARCHAR(4),
    verified_tick   BOOLEAN      NOT NULL DEFAULT FALSE,
    is_manual       BOOLEAN      NOT NULL DEFAULT FALSE,       -- true = typed by hand, no longer follows the candidate
    source          VARCHAR(10)  NOT NULL DEFAULT 'MANUAL' CHECK (source IN ('CANDIDATE', 'MANUAL', 'API')),
    sort_order      INT          NOT NULL,
    UNIQUE (check_id, field_key)
);

CREATE TABLE check_details (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    check_id   UUID         NOT NULL REFERENCES verification_checks (id),
    label      VARCHAR(200) NOT NULL,
    value      TEXT,
    sort_order INT          NOT NULL
);
CREATE INDEX ix_check_details_check ON check_details (check_id, sort_order);

CREATE TABLE check_free_sections (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    check_id    UUID        NOT NULL REFERENCES verification_checks (id),
    kind        VARCHAR(10) NOT NULL CHECK (kind IN ('TEXT', 'IMAGE')),
    text_value  TEXT,
    document_id UUID,                                          -- documents module, Phase 5
    sort_order  INT         NOT NULL
);
CREATE INDEX ix_check_free_sections_check ON check_free_sections (check_id, sort_order);
