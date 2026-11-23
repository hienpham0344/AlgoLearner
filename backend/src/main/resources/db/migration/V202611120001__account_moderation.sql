ALTER TABLE tbl_account ADD COLUMN suspension_reason VARCHAR(500);
ALTER TABLE tbl_account ADD COLUMN suspension_ends_at TIMESTAMPTZ;
CREATE INDEX idx_account_suspension_expiry ON tbl_account(suspension_ends_at)
    WHERE status = 'SUSPENDED';

ALTER TABLE tbl_auth_session ADD COLUMN user_agent VARCHAR(255);

CREATE TABLE tbl_account_audit_log (
    id BIGSERIAL PRIMARY KEY,
    actor_id UUID REFERENCES tbl_account(id),
    target_id UUID NOT NULL REFERENCES tbl_account(id),
    action VARCHAR(32) NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_account_audit_actor ON tbl_account_audit_log(actor_id);
CREATE INDEX idx_account_audit_target ON tbl_account_audit_log(target_id);

ALTER TABLE tbl_account_audit_log ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE tbl_account_audit_log FROM PUBLIC;
REVOKE ALL ON SEQUENCE tbl_account_audit_log_id_seq FROM PUBLIC;
DO $$
DECLARE browser_role TEXT;
BEGIN
    FOREACH browser_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = browser_role) THEN
            EXECUTE format('REVOKE ALL ON TABLE tbl_account_audit_log FROM %I', browser_role);
            EXECUTE format('REVOKE ALL ON SEQUENCE tbl_account_audit_log_id_seq FROM %I', browser_role);
        END IF;
    END LOOP;
END $$;
