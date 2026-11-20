-- Minimal confirmed-evidence ledger for FR-PRO-005; suspension never implies a violation.
CREATE TABLE tbl_account_violation (
 id UUID PRIMARY KEY,
 target_account_id UUID NOT NULL REFERENCES tbl_account(id),
 confirmed_by UUID NOT NULL REFERENCES tbl_account(id),
 request_id UUID NOT NULL,
 reason VARCHAR(500) NOT NULL CHECK(length(trim(reason))>0),
 confirmed_at TIMESTAMPTZ NOT NULL,
 revoked_at TIMESTAMPTZ,
 revoked_by UUID REFERENCES tbl_account(id),
 revoke_reason VARCHAR(500),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(confirmed_by,request_id),
 CHECK((revoked_at IS NULL AND revoked_by IS NULL AND revoke_reason IS NULL)
    OR (revoked_at IS NOT NULL AND revoked_by IS NOT NULL AND revoke_reason IS NOT NULL AND length(trim(revoke_reason))>0))
);
CREATE INDEX idx_account_violation_target ON tbl_account_violation(target_account_id);
CREATE INDEX idx_account_violation_confirmed_by ON tbl_account_violation(confirmed_by);
CREATE INDEX idx_account_violation_revoked_by ON tbl_account_violation(revoked_by);
ALTER TABLE tbl_account_violation ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE tbl_account_violation FROM PUBLIC;
DO $$ DECLARE browser_role TEXT; BEGIN
 FOREACH browser_role IN ARRAY ARRAY['anon','authenticated'] LOOP
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=browser_role) THEN
   EXECUTE format('REVOKE ALL ON TABLE tbl_account_violation FROM %I',browser_role);
  END IF;
 END LOOP;
END $$;
