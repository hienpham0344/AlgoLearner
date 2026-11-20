CREATE TABLE tbl_public_course (
 id UUID PRIMARY KEY,
 author_id UUID NOT NULL REFERENCES tbl_account(id) ON DELETE RESTRICT,
 title VARCHAR(255) NOT NULL,
 description TEXT,
 status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','PENDING_REVIEW','PUBLISHED','REJECTED')),
 review_reason VARCHAR(500),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_public_course_author ON tbl_public_course(author_id,status);
CREATE INDEX idx_public_course_status ON tbl_public_course(status,created_at);
CREATE TABLE tbl_public_course_unit (
 id UUID PRIMARY KEY,
 course_id UUID NOT NULL REFERENCES tbl_public_course(id) ON DELETE RESTRICT,
 title VARCHAR(255) NOT NULL,
 content TEXT NOT NULL,
 display_order INTEGER NOT NULL CHECK(display_order>=0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(course_id,display_order)
);
CREATE TABLE tbl_course_enrollment (
 id UUID PRIMARY KEY,
 course_id UUID NOT NULL REFERENCES tbl_public_course(id) ON DELETE RESTRICT,
 account_id UUID NOT NULL REFERENCES tbl_account(id) ON DELETE RESTRICT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(course_id,account_id)
);
CREATE INDEX idx_course_enrollment_account ON tbl_course_enrollment(account_id);
CREATE TABLE tbl_course_unit_completion (
 id UUID PRIMARY KEY,
 enrollment_id UUID NOT NULL REFERENCES tbl_course_enrollment(id) ON DELETE RESTRICT,
 unit_id UUID NOT NULL REFERENCES tbl_public_course_unit(id) ON DELETE RESTRICT,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(enrollment_id,unit_id)
);
CREATE INDEX idx_course_completion_unit ON tbl_course_unit_completion(unit_id);
DO $$
DECLARE tbl TEXT; browser_role TEXT;
BEGIN
 FOREACH tbl IN ARRAY ARRAY['tbl_public_course','tbl_public_course_unit','tbl_course_enrollment','tbl_course_unit_completion'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',tbl);
  EXECUTE format('REVOKE ALL ON TABLE %I FROM PUBLIC',tbl);
  FOREACH browser_role IN ARRAY ARRAY['anon','authenticated'] LOOP
   IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=browser_role) THEN
    EXECUTE format('REVOKE ALL ON TABLE %I FROM %I',tbl,browser_role);
   END IF;
  END LOOP;
 END LOOP;
END $$;
