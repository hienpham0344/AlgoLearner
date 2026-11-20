CREATE TABLE tbl_stored_file (
    id UUID PRIMARY KEY,
    uploader_id UUID NOT NULL REFERENCES tbl_account(id) ON DELETE RESTRICT,
    classroom_id UUID NOT NULL REFERENCES tbl_classroom(id) ON DELETE RESTRICT,
    object_key VARCHAR(80) NOT NULL UNIQUE,
    file_name VARCHAR(255) NOT NULL,
    media_type VARCHAR(150) NOT NULL,
    kind VARCHAR(20) NOT NULL CHECK (kind IN ('PDF','DOCX','PPTX','ZIP','IMAGE')),
    byte_size BIGINT NOT NULL CHECK (byte_size > 0 AND byte_size <= 20971520),
    sha256 VARCHAR(64) NOT NULL CHECK (sha256 ~ '^[a-f0-9]{64}$'),
    purpose VARCHAR(30) NOT NULL CHECK (purpose IN ('LESSON_MATERIAL','ESSAY_SUBMISSION')),
    state VARCHAR(20) NOT NULL CHECK (state IN ('PENDING','READY','REJECTED','DELETING')),
    object_uploaded BOOLEAN NOT NULL DEFAULT FALSE,
    scan_attempts INTEGER NOT NULL DEFAULT 0 CHECK (scan_attempts >= 0),
    processing_token UUID,
    processing_until TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_stored_file_uploader ON tbl_stored_file(uploader_id);
CREATE INDEX idx_stored_file_classroom ON tbl_stored_file(classroom_id);
CREATE INDEX idx_stored_file_pending ON tbl_stored_file(state, updated_at);

CREATE TABLE tbl_file_policy (
    id UUID PRIMARY KEY,
    max_bytes BIGINT NOT NULL CHECK (max_bytes > 0 AND max_bytes <= 20971520),
    allowed_kinds VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO tbl_file_policy(id,max_bytes,allowed_kinds)
VALUES ('00000000-0000-0000-0000-000000000001',20971520,'PDF,DOCX,PPTX,ZIP,IMAGE');

ALTER TABLE tbl_stored_file ENABLE ROW LEVEL SECURITY;
ALTER TABLE tbl_file_policy ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON tbl_stored_file,tbl_file_policy FROM PUBLIC;
DO $$
DECLARE browser_role TEXT;
BEGIN
    FOREACH browser_role IN ARRAY ARRAY['anon','authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname=browser_role) THEN
            EXECUTE format('REVOKE ALL ON tbl_stored_file,tbl_file_policy FROM %I',browser_role);
        END IF;
    END LOOP;
END $$;
