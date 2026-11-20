-- Existing grades and answer rows remain unchanged. Historical content cannot be asserted to be an original snapshot.
CREATE TABLE tbl_assignment_revision (
    id UUID PRIMARY KEY,
    assignment_id UUID NOT NULL REFERENCES tbl_assignment(id),
    content JSONB NOT NULL,
    provenance VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_assignment_revision_assignment ON tbl_assignment_revision(assignment_id);
ALTER TABLE tbl_assignment ADD COLUMN published_revision_id UUID REFERENCES tbl_assignment_revision(id);
CREATE INDEX idx_assignment_published_revision ON tbl_assignment(published_revision_id);
ALTER TABLE tbl_assignment_attempt ADD COLUMN revision_id UUID REFERENCES tbl_assignment_revision(id);
ALTER TABLE tbl_assignment_attempt ADD COLUMN revision_snapshot JSONB;
ALTER TABLE tbl_assignment_attempt ADD COLUMN snapshot_provenance VARCHAR(30) NOT NULL DEFAULT 'LEGACY_MISSING';
ALTER TABLE tbl_assignment_attempt ADD COLUMN base_expires_at TIMESTAMPTZ;
CREATE INDEX idx_assignment_attempt_revision ON tbl_assignment_attempt(revision_id);
ALTER TABLE tbl_assignment_answer ADD COLUMN stored_file_id UUID REFERENCES tbl_stored_file(id);
CREATE INDEX idx_assignment_answer_stored_file ON tbl_assignment_answer(stored_file_id);
CREATE FUNCTION forbid_assignment_revision_mutation() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'Assignment revision is immutable';
END $$;
CREATE TRIGGER trg_assignment_revision_immutable BEFORE UPDATE OR DELETE ON tbl_assignment_revision
    FOR EACH ROW EXECUTE FUNCTION forbid_assignment_revision_mutation();

CREATE FUNCTION protect_attempt_revision_snapshot() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.revision_id IS DISTINCT FROM NEW.revision_id
       OR OLD.revision_snapshot IS DISTINCT FROM NEW.revision_snapshot
       OR OLD.snapshot_provenance IS DISTINCT FROM NEW.snapshot_provenance
       OR OLD.base_expires_at IS DISTINCT FROM NEW.base_expires_at THEN
        RAISE EXCEPTION 'Attempt revision snapshot is immutable';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_attempt_revision_snapshot_immutable BEFORE UPDATE ON tbl_assignment_attempt
    FOR EACH ROW EXECUTE FUNCTION protect_attempt_revision_snapshot();
ALTER TABLE tbl_assignment_revision ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON tbl_assignment_revision FROM PUBLIC;
DO $$
DECLARE browser_role TEXT;
BEGIN
    FOREACH browser_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = browser_role) THEN
            EXECUTE format('REVOKE ALL ON TABLE tbl_assignment_revision FROM %I', browser_role);
        END IF;
    END LOOP;
END $$;
