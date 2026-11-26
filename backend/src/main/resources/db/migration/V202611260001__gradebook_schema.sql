CREATE TABLE tbl_gradebook (
    id UUID PRIMARY KEY,
    classroom_id UUID NOT NULL UNIQUE REFERENCES tbl_classroom(id) ON DELETE CASCADE,
    active_scheme_revision_id UUID,
    show_overall BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tbl_grade_scheme_revision (
    id UUID PRIMARY KEY,
    gradebook_id UUID NOT NULL REFERENCES tbl_gradebook(id),
    revision_no INT NOT NULL,
    mode VARCHAR NOT NULL CHECK (mode IN ('WEIGHTED', 'RAW')),
    created_by UUID NOT NULL REFERENCES tbl_account(id),
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (gradebook_id, revision_no),
    UNIQUE (id, gradebook_id)
);
CREATE INDEX idx_grade_scheme_revision_creator ON tbl_grade_scheme_revision(created_by);

ALTER TABLE tbl_gradebook ADD CONSTRAINT fk_gradebook_active_revision
    FOREIGN KEY (active_scheme_revision_id, id) REFERENCES tbl_grade_scheme_revision(id, gradebook_id);
CREATE INDEX idx_gradebook_active_revision ON tbl_gradebook(active_scheme_revision_id, id);

CREATE TABLE tbl_grade_scheme_item (
    id UUID PRIMARY KEY,
    revision_id UUID NOT NULL REFERENCES tbl_grade_scheme_revision(id),
    source_type VARCHAR NOT NULL CHECK (source_type IN ('ASSIGNMENT', 'QUIZ')),
    source_id UUID NOT NULL,
    weight NUMERIC(7,4) NOT NULL CHECK (weight BETWEEN 0 AND 100),
    required BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (revision_id, source_type, source_id)
);

CREATE FUNCTION forbid_grade_scheme_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Grade scheme revisions and items are immutable' USING ERRCODE = '23514';
END $$;

CREATE TRIGGER trg_grade_scheme_revision_immutable
    BEFORE UPDATE OR DELETE ON tbl_grade_scheme_revision
    FOR EACH ROW EXECUTE FUNCTION forbid_grade_scheme_mutation();
CREATE TRIGGER trg_grade_scheme_item_immutable
    BEFORE UPDATE OR DELETE ON tbl_grade_scheme_item
    FOR EACH ROW EXECUTE FUNCTION forbid_grade_scheme_mutation();

INSERT INTO tbl_gradebook (id, classroom_id)
SELECT gen_random_uuid(), id FROM tbl_classroom;

ALTER TABLE tbl_gradebook ENABLE ROW LEVEL SECURITY;
ALTER TABLE tbl_grade_scheme_revision ENABLE ROW LEVEL SECURITY;
ALTER TABLE tbl_grade_scheme_item ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON TABLE tbl_gradebook, tbl_grade_scheme_revision, tbl_grade_scheme_item FROM PUBLIC;
DO $$
DECLARE browser_role TEXT;
BEGIN
    FOREACH browser_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = browser_role) THEN
            EXECUTE format('REVOKE ALL ON TABLE tbl_gradebook, tbl_grade_scheme_revision, tbl_grade_scheme_item FROM %I', browser_role);
        END IF;
    END LOOP;
END $$;
