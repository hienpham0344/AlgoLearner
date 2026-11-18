-- Refuse same-namespace case collisions; preserve every legacy identifier unchanged.
-- Cross-namespace collisions remain readable through an explicit IdentityType.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM tbl_account GROUP BY lower(email) HAVING count(*) > 1)
       OR EXISTS (SELECT 1 FROM tbl_account GROUP BY lower(username) HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Account identity case collisions require explicit operator reconciliation before migration';
    END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uq_account_email_lower ON tbl_account (lower(email));
CREATE UNIQUE INDEX IF NOT EXISTS uq_account_username_lower ON tbl_account (lower(username));
