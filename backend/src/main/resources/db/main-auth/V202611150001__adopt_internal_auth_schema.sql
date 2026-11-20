-- Bridge databases that already ran the auth migrations merged to main.
-- Keep the legacy tables and token hashes for audit; old sessions are revoked
-- because the new application uses an HttpOnly-cookie refresh protocol.
ALTER TABLE tbl_account ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE tbl_account ADD COLUMN IF NOT EXISTS login_locked_until TIMESTAMPTZ;
ALTER TABLE tbl_account ADD COLUMN IF NOT EXISTS full_name VARCHAR(150);
ALTER TABLE tbl_account ADD COLUMN IF NOT EXISTS short_bio VARCHAR(200);
ALTER TABLE tbl_account ADD COLUMN IF NOT EXISTS avatar_key VARCHAR(80);
ALTER TABLE tbl_account ADD COLUMN IF NOT EXISTS profile_public BOOLEAN NOT NULL DEFAULT FALSE;
CREATE UNIQUE INDEX IF NOT EXISTS uq_account_email_lower ON tbl_account (lower(email));
CREATE UNIQUE INDEX IF NOT EXISTS uq_account_username_lower ON tbl_account (lower(username));

UPDATE tbl_account SET failed_login_attempts = failed_login_count;
UPDATE tbl_account account SET login_locked_until = restriction.ends_at
FROM (
    SELECT DISTINCT ON (account_id) account_id, ends_at
    FROM tbl_account_restriction
    WHERE kind = 'AUTO_LOCK' AND revoked_at IS NULL AND ends_at > CURRENT_TIMESTAMP
    ORDER BY account_id, ends_at DESC
) restriction
WHERE account.id = restriction.account_id;

CREATE TABLE IF NOT EXISTS tbl_registration_otp (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES tbl_account(id),
    code_hash VARCHAR(100) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    used_at TIMESTAMPTZ,
    purpose VARCHAR(24) NOT NULL DEFAULT 'REGISTER',
    reset_token_hash VARCHAR(64) UNIQUE,
    reset_expires_at TIMESTAMPTZ,
    reset_used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_registration_otp_account_id ON tbl_registration_otp(account_id);
CREATE INDEX IF NOT EXISTS idx_otp_account_purpose_sent_internal ON tbl_registration_otp(account_id, purpose, sent_at DESC);
INSERT INTO tbl_registration_otp
    (id, account_id, code_hash, expires_at, sent_at, failed_attempts, used_at, purpose, created_at, updated_at)
SELECT id, account_id, code_hash, expires_at, sent_at, failed_attempts, consumed_at, purpose, created_at, updated_at
FROM tbl_otp_challenge
ON CONFLICT (id) DO NOTHING;

ALTER TABLE tbl_auth_session ADD COLUMN IF NOT EXISTS refresh_hash VARCHAR(64);
UPDATE tbl_auth_session
SET refresh_hash = md5(id::text) || md5(id::text || ':legacy'),
    revoked_at = COALESCE(revoked_at, CURRENT_TIMESTAMP)
WHERE refresh_hash IS NULL;
ALTER TABLE tbl_auth_session ALTER COLUMN refresh_hash SET NOT NULL;
ALTER TABLE tbl_auth_session ALTER COLUMN refresh_token_hash DROP NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS uq_auth_session_refresh_hash ON tbl_auth_session(refresh_hash);
CREATE INDEX IF NOT EXISTS idx_auth_session_account ON tbl_auth_session(account_id);
