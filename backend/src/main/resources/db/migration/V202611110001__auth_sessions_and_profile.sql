ALTER TABLE tbl_account ADD COLUMN full_name VARCHAR(150);
ALTER TABLE tbl_account ADD COLUMN short_bio VARCHAR(200);
ALTER TABLE tbl_account ADD COLUMN avatar_key VARCHAR(80);
ALTER TABLE tbl_account ADD COLUMN profile_public BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE tbl_registration_otp ADD COLUMN purpose VARCHAR(24) NOT NULL DEFAULT 'REGISTER';
ALTER TABLE tbl_registration_otp ADD COLUMN reset_token_hash VARCHAR(64) UNIQUE;
ALTER TABLE tbl_registration_otp ADD COLUMN reset_expires_at TIMESTAMPTZ;
ALTER TABLE tbl_registration_otp ADD COLUMN reset_used_at TIMESTAMPTZ;
CREATE INDEX idx_otp_account_purpose_sent ON tbl_registration_otp(account_id, purpose, sent_at DESC);

CREATE TABLE tbl_auth_session (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES tbl_account(id),
    refresh_hash VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    remember_me BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_auth_session_account ON tbl_auth_session(account_id);
