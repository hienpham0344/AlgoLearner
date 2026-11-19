ALTER TABLE tbl_account ADD COLUMN suspension_reason VARCHAR(500);
ALTER TABLE tbl_account ADD COLUMN suspension_ends_at TIMESTAMPTZ;
UPDATE tbl_account account
SET suspension_reason = LEFT(restriction.reason, 500),
    suspension_ends_at = restriction.ends_at
FROM (
    SELECT DISTINCT ON (account_id) account_id, reason, ends_at
    FROM tbl_account_restriction
    WHERE kind IN ('ADMIN_LOCK', 'SUSPENSION') AND revoked_at IS NULL
      AND (ends_at IS NULL OR ends_at > CURRENT_TIMESTAMP)
    ORDER BY account_id, starts_at DESC
) restriction
WHERE account.id = restriction.account_id AND account.status = 'SUSPENDED';
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

INSERT INTO tbl_account_audit_log (actor_id, target_id, action, reason, created_at, updated_at)
SELECT actor_account_id, target_account_id, LEFT(action, 32),
       LEFT('Legacy outcome: ' || outcome, 500), occurred_at, occurred_at
FROM tbl_audit_log
WHERE target_account_id IS NOT NULL;

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
