-- Enhance tbl_account for Auth Module 4.1
ALTER TABLE tbl_account DROP CONSTRAINT IF EXISTS tbl_account_status_check;
ALTER TABLE tbl_account ADD CONSTRAINT tbl_account_status_check 
    CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED'));

ALTER TABLE tbl_account ADD COLUMN IF NOT EXISTS password_hash TEXT NOT NULL DEFAULT '';
ALTER TABLE tbl_account ADD COLUMN IF NOT EXISTS failed_login_count INT NOT NULL DEFAULT 0;
ALTER TABLE tbl_account ADD COLUMN IF NOT EXISTS email_verified_at TIMESTAMPTZ;
ALTER TABLE tbl_account ADD COLUMN IF NOT EXISTS terms_accepted_at TIMESTAMPTZ;
ALTER TABLE tbl_account ADD COLUMN IF NOT EXISTS privacy_accepted_at TIMESTAMPTZ;

-- OTP Challenges
CREATE TABLE IF NOT EXISTS tbl_otp_challenge (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES tbl_account(id) ON DELETE RESTRICT,
    purpose VARCHAR(20) NOT NULL CHECK (purpose IN ('REGISTER', 'RESET_PASSWORD')),
    code_hash TEXT NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    sent_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_otp_account_purpose_sent ON tbl_otp_challenge(account_id, purpose, sent_at);

-- Auth Sessions
CREATE TABLE IF NOT EXISTS tbl_auth_session (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES tbl_account(id) ON DELETE RESTRICT,
    refresh_token_hash TEXT NOT NULL UNIQUE,
    remember_me BOOLEAN NOT NULL DEFAULT FALSE,
    device_label VARCHAR(255),
    expires_at TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_sessions_account_revoked ON tbl_auth_session(account_id, revoked_at);

-- Account Restrictions (AUTO_LOCK, ADMIN_LOCK, SUSPENSION)
CREATE TABLE IF NOT EXISTS tbl_account_restriction (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES tbl_account(id) ON DELETE RESTRICT,
    imposed_by_account_id UUID REFERENCES tbl_account(id) ON DELETE RESTRICT,
    kind VARCHAR(30) NOT NULL CHECK (kind IN ('AUTO_LOCK', 'ADMIN_LOCK', 'SUSPENSION')),
    reason TEXT,
    starts_at TIMESTAMPTZ NOT NULL,
    ends_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_restrictions_account_kind_end ON tbl_account_restriction(account_id, kind, ends_at);
CREATE INDEX IF NOT EXISTS idx_restrictions_imposed_by ON tbl_account_restriction(imposed_by_account_id);

-- Audit Logs
CREATE TABLE IF NOT EXISTS tbl_audit_log (
    id UUID PRIMARY KEY,
    actor_account_id UUID REFERENCES tbl_account(id) ON DELETE RESTRICT,
    target_account_id UUID REFERENCES tbl_account(id) ON DELETE RESTRICT,
    actor_type VARCHAR(20) NOT NULL CHECK (actor_type IN ('USER', 'SYSTEM')),
    action VARCHAR(100) NOT NULL,
    outcome VARCHAR(20) NOT NULL CHECK (outcome IN ('SUCCESS', 'REJECTED', 'FAILED')),
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_audit_actor_time ON tbl_audit_log(actor_account_id, occurred_at);
CREATE INDEX IF NOT EXISTS idx_audit_target_time ON tbl_audit_log(target_account_id, occurred_at);
