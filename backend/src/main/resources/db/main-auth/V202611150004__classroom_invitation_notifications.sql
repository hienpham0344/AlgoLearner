CREATE TABLE tbl_notification (
    id UUID PRIMARY KEY,
    recipient_id UUID NOT NULL REFERENCES tbl_account(id),
    classroom_invitation_id UUID NOT NULL UNIQUE REFERENCES tbl_classroom_invitation(id),
    type VARCHAR(40) NOT NULL CHECK (type = 'CLASS_INVITATION'),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notification_recipient_id ON tbl_notification(recipient_id);
CREATE INDEX idx_notification_created_at ON tbl_notification(created_at DESC);

ALTER TABLE tbl_notification ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE tbl_notification FROM PUBLIC;
DO $$
DECLARE browser_role TEXT;
BEGIN
    FOREACH browser_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = browser_role) THEN
            EXECUTE format('REVOKE ALL ON TABLE tbl_notification FROM %I', browser_role);
        END IF;
    END LOOP;
END $$;
