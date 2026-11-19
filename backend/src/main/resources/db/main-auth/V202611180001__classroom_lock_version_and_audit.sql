ALTER TABLE tbl_classroom ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

-- Main-auth originally used a UUID security-audit table. Preserve its complete shape and rows.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_schema = current_schema() AND table_name = 'tbl_audit_log'
                 AND column_name = 'id' AND data_type = 'uuid') THEN
        ALTER TABLE tbl_audit_log RENAME TO tbl_legacy_auth_audit_log;
        ALTER INDEX tbl_audit_log_pkey RENAME TO tbl_legacy_auth_audit_log_pkey;
    END IF;
END $$;

CREATE TABLE tbl_audit_log (
    id BIGSERIAL PRIMARY KEY,
    legacy_id UUID UNIQUE,
    outcome VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    actor_id UUID REFERENCES tbl_account(id),
    actor_role VARCHAR(20) NOT NULL,
    target_type VARCHAR(40) NOT NULL,
    target_id VARCHAR(80) NOT NULL,
    action VARCHAR(100) NOT NULL,
    before_state TEXT,
    after_state TEXT,
    reason VARCHAR(500),
    correlation_id VARCHAR(128),
    client_ip VARCHAR(45),
    user_agent VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
DO $$
BEGIN
    IF to_regclass('tbl_legacy_auth_audit_log') IS NOT NULL THEN
        INSERT INTO tbl_audit_log(legacy_id, actor_id, actor_role, target_type, target_id,
                                  action, outcome, before_state, created_at, updated_at)
        SELECT id, actor_account_id, actor_type, 'ACCOUNT',
               COALESCE(target_account_id::text, 'SYSTEM'), action, outcome,
               json_build_object('actorType', actor_type, 'targetAccountId', target_account_id)::text,
               occurred_at, occurred_at
        FROM tbl_legacy_auth_audit_log;
    END IF;
END $$;
CREATE INDEX idx_audit_log_actor ON tbl_audit_log(actor_id);
CREATE INDEX idx_audit_log_target ON tbl_audit_log(target_type, target_id, created_at);
ALTER TABLE tbl_audit_log ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE tbl_audit_log FROM PUBLIC;
REVOKE ALL ON SEQUENCE tbl_audit_log_id_seq FROM PUBLIC;
DO $$
DECLARE browser_role TEXT;
BEGIN
    FOREACH browser_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = browser_role) THEN
            EXECUTE format('REVOKE ALL ON TABLE tbl_audit_log FROM %I', browser_role);
            EXECUTE format('REVOKE ALL ON SEQUENCE tbl_audit_log_id_seq FROM %I', browser_role);
        END IF;
    END LOOP;
END $$;
