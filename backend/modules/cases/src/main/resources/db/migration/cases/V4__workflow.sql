-- The review workflow (CLAUDE.md section 11.3): who sent a case for review, who decided, when, and an
-- append-only history of every change of status. No foreign keys to other modules: admin ids are plain ids.

ALTER TABLE cases
    ADD COLUMN submitted_by UUID,
    ADD COLUMN submitted_at TIMESTAMPTZ,
    ADD COLUMN reviewed_by  UUID,          -- who approved or asked for changes last
    ADD COLUMN reviewed_at  TIMESTAMPTZ,
    ADD COLUMN approved_at  TIMESTAMPTZ,   -- set on approval, cleared when the case goes back to work
    ADD COLUMN finalized_by UUID,
    ADD COLUMN finalized_at TIMESTAMPTZ;

CREATE TABLE case_status_history (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id     UUID        NOT NULL REFERENCES cases (id),
    action      VARCHAR(20) NOT NULL CHECK (action IN ('SUBMIT', 'APPROVE', 'REQUEST_CHANGES', 'FINALIZE', 'REOPEN')),
    from_status VARCHAR(30) NOT NULL,
    to_status   VARCHAR(30) NOT NULL,
    actor_id    UUID        NOT NULL,
    comment     TEXT,
    report_version INT,                     -- FINALIZE: the final report version
    at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_case_history_case ON case_status_history (case_id, at);

-- Nobody can edit or delete the history, not even by mistake (same rule as the audit log).
CREATE FUNCTION forbid_history_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'case_status_history is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER case_status_history_no_change
    BEFORE UPDATE OR DELETE ON case_status_history
    FOR EACH ROW EXECUTE FUNCTION forbid_history_change();
