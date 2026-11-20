ALTER TABLE tbl_account ADD COLUMN password_hash VARCHAR(100);
ALTER TABLE tbl_account ADD COLUMN failed_login_attempts INTEGER NOT NULL DEFAULT 0;
ALTER TABLE tbl_account ADD COLUMN login_locked_until TIMESTAMPTZ;
CREATE UNIQUE INDEX uq_account_email_lower ON tbl_account (lower(email));
CREATE UNIQUE INDEX uq_account_username_lower ON tbl_account (lower(username));
ALTER TABLE tbl_account DROP CONSTRAINT tbl_account_status_check;
ALTER TABLE tbl_account ADD CONSTRAINT tbl_account_status_check
    CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED', 'DEACTIVATED'));

CREATE TABLE tbl_registration_otp (
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL REFERENCES tbl_account(id),
    code_hash VARCHAR(100) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ NOT NULL,
    failed_attempts INTEGER NOT NULL DEFAULT 0,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_registration_otp_account_id ON tbl_registration_otp(account_id);
