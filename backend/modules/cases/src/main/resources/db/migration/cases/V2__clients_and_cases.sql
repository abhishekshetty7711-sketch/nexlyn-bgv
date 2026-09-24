-- Clients, cases, candidates, assignments and the Report ID sequence (CLAUDE.md §10 "schema cases").
-- Verification checks and their fields arrive in Phase 4. Admin ids are plain UUIDs: there are no
-- foreign keys to other schemas (§4.2 rule 4).

CREATE TABLE clients (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                VARCHAR(200) NOT NULL,
    display_name        TEXT         NOT NULL,                 -- as printed on reports; may span several lines
    logo_document_id    UUID,                                  -- documents module, Phase 5
    default_check_types JSONB        NOT NULL DEFAULT '[]'::jsonb,
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_clients_name ON clients (lower(name));

CREATE TABLE cases (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_id                VARCHAR(30)  NOT NULL,
    client_id                UUID         NOT NULL REFERENCES clients (id),
    company_display_name     TEXT,                              -- overrides the client's display name for this report only
    issue_date               DATE         NOT NULL,
    period_show              BOOLEAN      NOT NULL DEFAULT TRUE,
    period_start             DATE,
    period_end               DATE,
    status_preset            VARCHAR(20)  NOT NULL DEFAULT 'COMPLETED'
                             CHECK (status_preset IN ('COMPLETED', 'DISCREPANCY', 'UNABLE', 'CLOSED')),
    status_title             VARCHAR(100) NOT NULL DEFAULT 'Completed',
    status_subtitle          VARCHAR(200) NOT NULL DEFAULT 'All Requested Verifications Completed',
    total_override           INT CHECK (total_override >= 0),
    completed_override       INT CHECK (completed_override >= 0),
    overall_status_override  VARCHAR(100),
    analyst_remarks          TEXT,
    final_recommendation     TEXT,
    layout_cards             SMALLINT     NOT NULL DEFAULT 4 CHECK (layout_cards IN (4, 6)),
    date_format              VARCHAR(10)  NOT NULL DEFAULT 'NUMERIC' CHECK (date_format IN ('NUMERIC', 'TEXT')),
    watermark_enabled        BOOLEAN      NOT NULL DEFAULT FALSE,
    watermark_text           VARCHAR(40)  NOT NULL DEFAULT 'NEXLYN VERIFIED',
    lifecycle                VARCHAR(30)  NOT NULL DEFAULT 'DRAFT'
                             CHECK (lifecycle IN ('DRAFT', 'IN_REVIEW', 'CHANGES_REQUESTED', 'APPROVED', 'FINALIZED')),
    review_comment           TEXT,
    due_date                 DATE,
    saved_sections           JSONB        NOT NULL DEFAULT '{}'::jsonb,   -- section key -> when it was last saved
    deleted_at               TIMESTAMPTZ,
    created_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by               UUID,
    updated_by               UUID,
    version                  BIGINT       NOT NULL DEFAULT 0
);
-- Report IDs are unique across all cases, deleted ones included, so an ID is never reused.
CREATE UNIQUE INDEX ux_cases_report_id ON cases (upper(report_id));
CREATE INDEX ix_cases_client ON cases (client_id);
CREATE INDEX ix_cases_lifecycle ON cases (lifecycle) WHERE deleted_at IS NULL;
CREATE INDEX ix_cases_updated ON cases (updated_at DESC) WHERE deleted_at IS NULL;

CREATE TABLE candidates (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id           UUID         NOT NULL UNIQUE REFERENCES cases (id),
    full_name         VARCHAR(200),
    parent_type       VARCHAR(10)  NOT NULL DEFAULT 'FATHER' CHECK (parent_type IN ('FATHER', 'GUARDIAN')),
    parent_name       VARCHAR(200),
    employee_id       VARCHAR(50),
    dob               DATE,
    phone             VARCHAR(20),
    photo_document_id UUID,                                    -- documents module, Phase 5
    street            VARCHAR(300),
    city              VARCHAR(100),
    state             VARCHAR(100),
    pin               VARCHAR(6),
    country           VARCHAR(100) NOT NULL DEFAULT 'India',
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version           BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX ix_candidates_name ON candidates (lower(full_name));
CREATE INDEX ix_candidates_employee ON candidates (lower(employee_id));

CREATE TABLE case_assignments (
    case_id      UUID        NOT NULL REFERENCES cases (id),
    admin_id     UUID        NOT NULL,                          -- an auth admin; no cross-schema key
    role_in_case VARCHAR(10) NOT NULL CHECK (role_in_case IN ('PREPARER', 'REVIEWER')),
    assigned_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    assigned_by  UUID,
    PRIMARY KEY (case_id, admin_id, role_in_case)
);
CREATE INDEX ix_case_assignments_admin ON case_assignments (admin_id);

-- One counter per year for Report IDs of the form NX-YYYY-NNNN.
CREATE TABLE report_id_sequences (
    year       INT PRIMARY KEY,
    next_value INT NOT NULL CHECK (next_value >= 1)
);
