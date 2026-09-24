-- Auth & RBAC tables (CLAUDE.md §10 "schema auth", §11).
-- Conventions: UUID keys, snake_case, timestamptz, optimistic-lock "version" on mutable tables,
-- no foreign keys across schemas (only within "auth").

-- ---------------------------------------------------------------------------
-- Admins, roles, permissions
-- ---------------------------------------------------------------------------
CREATE TABLE admins (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email               VARCHAR(254) NOT NULL,
    full_name           VARCHAR(200) NOT NULL,
    password_hash       VARCHAR(255) NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                        CHECK (status IN ('ACTIVE', 'DISABLED', 'LOCKED')),
    failed_attempts     INT          NOT NULL DEFAULT 0 CHECK (failed_attempts >= 0),
    lockout_count       INT          NOT NULL DEFAULT 0 CHECK (lockout_count >= 0), -- drives escalating lockout
    locked_until        TIMESTAMPTZ,
    mfa_enabled         BOOLEAN      NOT NULL DEFAULT FALSE,
    password_changed_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_login_at       TIMESTAMPTZ,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by          UUID,
    updated_by          UUID,
    version             BIGINT       NOT NULL DEFAULT 0
);
-- Emails are compared case-insensitively.
CREATE UNIQUE INDEX ux_admins_email ON admins (lower(email));

CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(50)  NOT NULL UNIQUE,
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    system_role BOOLEAN      NOT NULL DEFAULT FALSE, -- seeded roles cannot be deleted
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    created_by  UUID,
    updated_by  UUID,
    version     BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE permissions (
    code        VARCHAR(50) PRIMARY KEY,
    description VARCHAR(500) NOT NULL
);

CREATE TABLE role_permissions (
    role_id         UUID        NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission_code VARCHAR(50) NOT NULL REFERENCES permissions (code),
    PRIMARY KEY (role_id, permission_code)
);

CREATE TABLE admin_roles (
    admin_id UUID NOT NULL REFERENCES admins (id) ON DELETE CASCADE,
    role_id  UUID NOT NULL REFERENCES roles (id),
    PRIMARY KEY (admin_id, role_id)
);
CREATE INDEX ix_admin_roles_role ON admin_roles (role_id);

-- ---------------------------------------------------------------------------
-- Sessions / tokens / 2FA
-- ---------------------------------------------------------------------------
-- family_id doubles as the session id ("sid" claim). All tokens rotated from one login share it.
CREATE TABLE refresh_tokens (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id          UUID         NOT NULL REFERENCES admins (id) ON DELETE CASCADE,
    family_id         UUID         NOT NULL,
    token_hash        VARCHAR(128) NOT NULL UNIQUE, -- hash only, never the raw token
    family_started_at TIMESTAMPTZ  NOT NULL,        -- start of the login: enforces the 12 h absolute limit
    issued_at         TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at        TIMESTAMPTZ  NOT NULL,
    revoked_at        TIMESTAMPTZ,
    replaced_by       UUID REFERENCES refresh_tokens (id),
    ip                VARCHAR(45),
    user_agent        VARCHAR(500)
);
CREATE INDEX ix_refresh_tokens_admin ON refresh_tokens (admin_id);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);

CREATE TABLE totp_secrets (
    admin_id         UUID PRIMARY KEY REFERENCES admins (id) ON DELETE CASCADE,
    secret_encrypted VARCHAR(500) NOT NULL, -- AES-256-GCM ciphertext, never the plain secret
    confirmed_at     TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE backup_codes (
    id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id  UUID         NOT NULL REFERENCES admins (id) ON DELETE CASCADE,
    code_hash VARCHAR(255) NOT NULL,
    used_at   TIMESTAMPTZ
);
CREATE INDEX ix_backup_codes_admin ON backup_codes (admin_id);

CREATE TABLE invitations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(254) NOT NULL,
    role_ids    UUID[]       NOT NULL,
    token_hash  VARCHAR(128) NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ  NOT NULL,
    accepted_at TIMESTAMPTZ,
    invited_by  UUID REFERENCES admins (id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_invitations_email ON invitations (lower(email));

CREATE TABLE login_attempts (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email   VARCHAR(254) NOT NULL,
    ip      VARCHAR(45),
    success BOOLEAN      NOT NULL,
    reason  VARCHAR(100),
    at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_login_attempts_email_at ON login_attempts (lower(email), at DESC);
CREATE INDEX ix_login_attempts_ip_at ON login_attempts (ip, at DESC);

-- ---------------------------------------------------------------------------
-- Audit log: append-only. Nobody (not even SUPER_ADMIN) can change or remove rows.
-- ---------------------------------------------------------------------------
CREATE TABLE audit_log (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    at             TIMESTAMPTZ  NOT NULL DEFAULT now(),
    actor_id       UUID,
    actor_email    VARCHAR(254),
    action         VARCHAR(100) NOT NULL,
    entity_type    VARCHAR(100),
    entity_id      VARCHAR(100),
    case_id        UUID,
    ip             VARCHAR(45),
    user_agent     VARCHAR(500),
    before         JSONB,
    after          JSONB,
    correlation_id VARCHAR(100)
);
CREATE INDEX ix_audit_log_at ON audit_log (at DESC);
CREATE INDEX ix_audit_log_actor ON audit_log (actor_id, at DESC);
CREATE INDEX ix_audit_log_entity ON audit_log (entity_type, entity_id);
CREATE INDEX ix_audit_log_case ON audit_log (case_id, at DESC);

CREATE FUNCTION audit_log_block_changes() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only: % is not allowed', TG_OP
        USING ERRCODE = 'restrict_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION audit_log_block_changes();

CREATE TRIGGER audit_log_no_truncate
    BEFORE TRUNCATE ON audit_log
    FOR EACH STATEMENT EXECUTE FUNCTION audit_log_block_changes();

-- ---------------------------------------------------------------------------
-- Seed: roles (§11.1), permissions and role -> permission grants (§11.2)
-- Code checks PERMISSIONS, never role names.
-- ---------------------------------------------------------------------------
INSERT INTO roles (code, name, description, system_role) VALUES
    ('SUPER_ADMIN', 'Super Admin',  'Owner: everything, including admins and roles', TRUE),
    ('OPS_MANAGER', 'Ops Manager',  'Operations: all cases, clients, assignment, finalize', TRUE),
    ('QC_REVIEWER', 'QC Reviewer',  'Reviews, approves and finalizes; does not prepare', TRUE),
    ('ANALYST',     'Analyst',      'Prepares assigned cases (data entry, documents, drafts)', TRUE),
    ('AUDITOR',     'Auditor',      'Read-only, PII masked, audit log access', TRUE);

INSERT INTO permissions (code, description) VALUES
    ('CASE_CREATE',              'Create cases'),
    ('CASE_READ_ALL',            'Read every case'),
    ('CASE_READ_ASSIGNED',       'Read cases assigned to the admin'),
    ('CASE_UPDATE',              'Edit case data (assigned cases only unless also CASE_READ_ALL)'),
    ('CASE_DELETE',              'Delete cases'),
    ('CASE_ASSIGN',              'Assign cases to admins'),
    ('CHECK_UPDATE',             'Add and edit verification checks'),
    ('DOCUMENT_UPLOAD',          'Upload documents'),
    ('DOCUMENT_DELETE',          'Delete documents'),
    ('PII_UNMASK',               'Reveal masked identifiers (audited)'),
    ('REPORT_GENERATE',          'Generate report previews and drafts'),
    ('REPORT_SUBMIT_FOR_REVIEW', 'Submit a case for review'),
    ('REPORT_APPROVE',           'Approve a case or request changes'),
    ('REPORT_FINALIZE',          'Finalize a case'),
    ('REPORT_DOWNLOAD_FINAL',    'Download finalized reports'),
    ('ATTESTATION_APPLY',        'Apply an attestation'),
    ('CLIENT_MANAGE',            'Manage clients'),
    ('SETTINGS_MANAGE',          'Manage system settings'),
    ('USER_MANAGE',              'Invite, edit, disable admins and revoke sessions'),
    ('ROLE_MANAGE',              'Create and edit roles and their permissions'),
    ('AUDIT_READ',               'Read the audit log');

INSERT INTO role_permissions (role_id, permission_code)
SELECT r.id, g.permission_code
FROM (VALUES
    ('SUPER_ADMIN', 'CASE_CREATE'), ('SUPER_ADMIN', 'CASE_READ_ALL'), ('SUPER_ADMIN', 'CASE_READ_ASSIGNED'),
    ('SUPER_ADMIN', 'CASE_UPDATE'), ('SUPER_ADMIN', 'CASE_DELETE'), ('SUPER_ADMIN', 'CASE_ASSIGN'),
    ('SUPER_ADMIN', 'CHECK_UPDATE'), ('SUPER_ADMIN', 'DOCUMENT_UPLOAD'), ('SUPER_ADMIN', 'DOCUMENT_DELETE'),
    ('SUPER_ADMIN', 'PII_UNMASK'), ('SUPER_ADMIN', 'REPORT_GENERATE'), ('SUPER_ADMIN', 'REPORT_SUBMIT_FOR_REVIEW'),
    ('SUPER_ADMIN', 'REPORT_APPROVE'), ('SUPER_ADMIN', 'REPORT_FINALIZE'), ('SUPER_ADMIN', 'REPORT_DOWNLOAD_FINAL'),
    ('SUPER_ADMIN', 'ATTESTATION_APPLY'), ('SUPER_ADMIN', 'CLIENT_MANAGE'), ('SUPER_ADMIN', 'SETTINGS_MANAGE'),
    ('SUPER_ADMIN', 'USER_MANAGE'), ('SUPER_ADMIN', 'ROLE_MANAGE'), ('SUPER_ADMIN', 'AUDIT_READ'),

    ('OPS_MANAGER', 'CASE_CREATE'), ('OPS_MANAGER', 'CASE_READ_ALL'), ('OPS_MANAGER', 'CASE_READ_ASSIGNED'),
    ('OPS_MANAGER', 'CASE_UPDATE'), ('OPS_MANAGER', 'CASE_ASSIGN'), ('OPS_MANAGER', 'CHECK_UPDATE'),
    ('OPS_MANAGER', 'DOCUMENT_UPLOAD'), ('OPS_MANAGER', 'DOCUMENT_DELETE'), ('OPS_MANAGER', 'PII_UNMASK'),
    ('OPS_MANAGER', 'REPORT_GENERATE'), ('OPS_MANAGER', 'REPORT_SUBMIT_FOR_REVIEW'), ('OPS_MANAGER', 'REPORT_APPROVE'),
    ('OPS_MANAGER', 'REPORT_FINALIZE'), ('OPS_MANAGER', 'REPORT_DOWNLOAD_FINAL'), ('OPS_MANAGER', 'ATTESTATION_APPLY'),
    ('OPS_MANAGER', 'CLIENT_MANAGE'),

    ('QC_REVIEWER', 'CASE_READ_ALL'), ('QC_REVIEWER', 'CASE_READ_ASSIGNED'), ('QC_REVIEWER', 'PII_UNMASK'),
    ('QC_REVIEWER', 'REPORT_GENERATE'), ('QC_REVIEWER', 'REPORT_APPROVE'), ('QC_REVIEWER', 'REPORT_FINALIZE'),
    ('QC_REVIEWER', 'REPORT_DOWNLOAD_FINAL'),

    ('ANALYST', 'CASE_CREATE'), ('ANALYST', 'CASE_READ_ASSIGNED'), ('ANALYST', 'CASE_UPDATE'),
    ('ANALYST', 'CHECK_UPDATE'), ('ANALYST', 'DOCUMENT_UPLOAD'), ('ANALYST', 'PII_UNMASK'),
    ('ANALYST', 'REPORT_GENERATE'), ('ANALYST', 'REPORT_SUBMIT_FOR_REVIEW'),

    ('AUDITOR', 'CASE_READ_ALL'), ('AUDITOR', 'REPORT_DOWNLOAD_FINAL'), ('AUDITOR', 'AUDIT_READ')
) AS g (role_code, permission_code)
JOIN roles r ON r.code = g.role_code;
