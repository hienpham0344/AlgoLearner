CREATE TABLE tbl_submission (
 id UUID PRIMARY KEY,
 owner_id UUID NOT NULL REFERENCES tbl_account(id),
 scope VARCHAR(30) NOT NULL CHECK(scope IN ('CLASS_ASSIGNMENT','CLASS_PRACTICE','PUBLIC_PROBLEM')),
 resource_id UUID NOT NULL,
 question_id UUID,
 attempt_id UUID,
 revision_id UUID NOT NULL,
 language VARCHAR(10) NOT NULL CHECK(language IN ('JAVA','PYTHON')),
 source TEXT NOT NULL,
 testcase_snapshot TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_submission_owner ON tbl_submission(owner_id);
CREATE INDEX idx_submission_resource ON tbl_submission(scope,resource_id);
CREATE TABLE tbl_judge_job (
 id UUID PRIMARY KEY,
 owner_id UUID NOT NULL REFERENCES tbl_account(id),
 submission_id UUID REFERENCES tbl_submission(id),
 request_key VARCHAR(128) NOT NULL,
 generation BIGINT NOT NULL CHECK(generation>=0),
 mode VARCHAR(10) NOT NULL CHECK(mode IN ('RUN','SUBMIT')),
 payload TEXT NOT NULL,
 status VARCHAR(30) NOT NULL DEFAULT 'QUEUED',
 attempts INTEGER NOT NULL DEFAULT 0,
 lease_token UUID,
 lease_until TIMESTAMPTZ,
 available_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 result TEXT,
 last_error VARCHAR(80),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(owner_id,request_key,generation,mode)
);
CREATE INDEX idx_judge_job_owner ON tbl_judge_job(owner_id);
CREATE INDEX idx_judge_job_submission ON tbl_judge_job(submission_id);
CREATE INDEX idx_judge_job_ready ON tbl_judge_job(status,available_at);
CREATE FUNCTION protect_submission_snapshot() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
 RAISE EXCEPTION 'Official submission snapshots are immutable';
END $$;
CREATE TRIGGER immutable_submission BEFORE UPDATE OR DELETE ON tbl_submission
 FOR EACH ROW EXECUTE FUNCTION protect_submission_snapshot();
ALTER TABLE tbl_submission ENABLE ROW LEVEL SECURITY;
ALTER TABLE tbl_judge_job ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON tbl_submission,tbl_judge_job FROM PUBLIC;
DO $$ DECLARE r TEXT; BEGIN
 FOREACH r IN ARRAY ARRAY['anon','authenticated'] LOOP
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=r) THEN
   EXECUTE format('REVOKE ALL ON tbl_submission,tbl_judge_job FROM %I',r);
  END IF;
 END LOOP;
END $$;

-- Assignment code jobs are declared in V180006; add the FK after the judge exists.
ALTER TABLE tbl_assignment_code_job ADD CONSTRAINT fk_assignment_code_job_judge FOREIGN KEY(job_id) REFERENCES tbl_judge_job(id);
