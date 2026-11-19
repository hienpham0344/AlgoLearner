ALTER TABLE tbl_account ADD COLUMN reset_token_hash VARCHAR(64);
ALTER TABLE tbl_account ADD COLUMN reset_token_expires_at TIMESTAMPTZ;
