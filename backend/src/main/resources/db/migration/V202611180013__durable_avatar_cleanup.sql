CREATE TABLE tbl_storage_cleanup_job (
    id UUID PRIMARY KEY,
    object_key VARCHAR(73) NOT NULL UNIQUE
        CHECK (object_key ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'CANCELLED', 'FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    lease_until TIMESTAMPTZ,
    lease_token UUID,
    last_error_code VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((status = 'PROCESSING' AND lease_until IS NOT NULL AND lease_token IS NOT NULL)
        OR (status <> 'PROCESSING' AND lease_until IS NULL AND lease_token IS NULL))
);
CREATE INDEX idx_storage_cleanup_pending ON tbl_storage_cleanup_job(available_at, created_at, id)
    WHERE status = 'PENDING';
CREATE INDEX idx_storage_cleanup_lease ON tbl_storage_cleanup_job(lease_until)
    WHERE status = 'PROCESSING';
CREATE INDEX idx_account_avatar_key ON tbl_account(avatar_key) WHERE avatar_key IS NOT NULL;
ALTER TABLE tbl_storage_cleanup_job ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON tbl_storage_cleanup_job FROM PUBLIC;
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
        EXECUTE 'REVOKE ALL ON tbl_storage_cleanup_job FROM anon';
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
        EXECUTE 'REVOKE ALL ON tbl_storage_cleanup_job FROM authenticated';
    END IF;
END $$;
