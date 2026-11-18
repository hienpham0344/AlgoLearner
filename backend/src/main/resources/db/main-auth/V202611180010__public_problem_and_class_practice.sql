CREATE TABLE tbl_public_problem (
 id UUID PRIMARY KEY,
 author_id UUID NOT NULL REFERENCES tbl_account(id),
 status VARCHAR(30) NOT NULL CHECK(status IN ('DRAFT','PENDING_REVIEW','PUBLISHED','REJECTED')),
 current_revision_id UUID,
 review_reason TEXT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_public_problem_author ON tbl_public_problem(author_id);
CREATE INDEX idx_public_problem_status ON tbl_public_problem(status,created_at DESC,id);
CREATE TABLE tbl_problem_revision (
 id UUID PRIMARY KEY,
 problem_id UUID NOT NULL REFERENCES tbl_public_problem(id),
 title VARCHAR(255) NOT NULL,
 payload TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_problem_revision_problem ON tbl_problem_revision(problem_id);
ALTER TABLE tbl_public_problem ADD CONSTRAINT fk_public_problem_revision FOREIGN KEY(current_revision_id) REFERENCES tbl_problem_revision(id);
CREATE INDEX idx_public_problem_revision ON tbl_public_problem(current_revision_id);
CREATE TABLE tbl_class_practice (
 id UUID PRIMARY KEY,
 classroom_id UUID NOT NULL REFERENCES tbl_classroom(id),
 source_problem_id UUID REFERENCES tbl_public_problem(id),
 revision_id UUID NOT NULL REFERENCES tbl_problem_revision(id),
 title VARCHAR(255) NOT NULL,
 payload TEXT NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','PUBLISHED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_class_practice_classroom ON tbl_class_practice(classroom_id);
CREATE INDEX idx_class_practice_source ON tbl_class_practice(source_problem_id);
CREATE INDEX idx_class_practice_revision ON tbl_class_practice(revision_id);
CREATE FUNCTION protect_problem_revision() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN RAISE EXCEPTION 'Problem revisions are immutable'; END $$;
CREATE TRIGGER immutable_problem_revision BEFORE UPDATE OR DELETE ON tbl_problem_revision FOR EACH ROW EXECUTE FUNCTION protect_problem_revision();
CREATE FUNCTION protect_practice_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 IF NEW.payload<>OLD.payload OR NEW.revision_id<>OLD.revision_id OR NEW.source_problem_id IS DISTINCT FROM OLD.source_problem_id THEN
  RAISE EXCEPTION 'Class practice source snapshots are immutable';
 END IF;
 RETURN NEW;
END $$;
CREATE TRIGGER immutable_practice_content BEFORE UPDATE ON tbl_class_practice FOR EACH ROW EXECUTE FUNCTION protect_practice_snapshot();
ALTER TABLE tbl_public_problem ENABLE ROW LEVEL SECURITY;
ALTER TABLE tbl_problem_revision ENABLE ROW LEVEL SECURITY;
ALTER TABLE tbl_class_practice ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON tbl_public_problem,tbl_problem_revision,tbl_class_practice FROM PUBLIC;
DO $$ DECLARE r TEXT; BEGIN
 FOREACH r IN ARRAY ARRAY['anon','authenticated'] LOOP
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=r) THEN
   EXECUTE format('REVOKE ALL ON tbl_public_problem,tbl_problem_revision,tbl_class_practice FROM %I',r);
  END IF;
 END LOOP;
END $$;
