CREATE TABLE tbl_quiz (
 id UUID PRIMARY KEY,
 classroom_id UUID NOT NULL REFERENCES tbl_classroom(id),
 title VARCHAR(255) NOT NULL,
 status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' CHECK(status IN('DRAFT','PUBLISHED','CLOSED')),
 graded BOOLEAN NOT NULL,
 opens_at TIMESTAMPTZ,
 closes_at TIMESTAMPTZ,
 duration_seconds INTEGER CHECK(duration_seconds>0),
 max_attempts INTEGER NOT NULL CHECK(max_attempts>0),
 allow_late BOOLEAN NOT NULL DEFAULT FALSE,
 shuffle_questions BOOLEAN NOT NULL DEFAULT FALSE,
 shuffle_answers BOOLEAN NOT NULL DEFAULT FALSE,
 release_policy VARCHAR(20) NOT NULL DEFAULT 'MANUAL' CHECK(release_policy IN('MANUAL','IMMEDIATE')),
 timeout_mode VARCHAR(20) NOT NULL DEFAULT 'AUTO_SUBMIT' CHECK(timeout_mode IN('AUTO_SUBMIT','LOCK')),
 questions_json JSONB NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CHECK(opens_at IS NULL OR closes_at IS NULL OR closes_at>opens_at)
);
CREATE INDEX idx_quiz_classroom ON tbl_quiz(classroom_id,status);
CREATE TABLE tbl_quiz_attempt (
 id UUID PRIMARY KEY,
 quiz_id UUID NOT NULL REFERENCES tbl_quiz(id),
 student_id UUID NOT NULL REFERENCES tbl_account(id),
 status VARCHAR(20) NOT NULL DEFAULT 'IN_PROGRESS' CHECK(status IN('IN_PROGRESS','SUBMITTED','LOCKED')),
 started_at TIMESTAMPTZ NOT NULL,
 cutoff_at TIMESTAMPTZ,
 submitted_at TIMESTAMPTZ,
 score NUMERIC(7,4) CHECK(score BETWEEN 0 AND 100),
 grade_published BOOLEAN NOT NULL DEFAULT FALSE,
 late BOOLEAN NOT NULL DEFAULT FALSE,
 snapshot_json JSONB NOT NULL,
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_quiz_attempt_quiz ON tbl_quiz_attempt(quiz_id);
CREATE INDEX idx_quiz_attempt_student ON tbl_quiz_attempt(student_id);
CREATE INDEX idx_quiz_attempt_timeout ON tbl_quiz_attempt(cutoff_at,id) WHERE status='IN_PROGRESS';
CREATE UNIQUE INDEX uq_quiz_active_attempt ON tbl_quiz_attempt(quiz_id,student_id) WHERE status='IN_PROGRESS';
CREATE TABLE tbl_quiz_answer (
 id UUID PRIMARY KEY,
 attempt_id UUID NOT NULL REFERENCES tbl_quiz_attempt(id),
 question_id UUID NOT NULL,
 selected_options JSONB NOT NULL,
 version BIGINT NOT NULL DEFAULT 1,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(attempt_id,question_id)
);
CREATE INDEX idx_quiz_answer_attempt ON tbl_quiz_answer(attempt_id);
CREATE TABLE tbl_quiz_save_receipt (
 id BIGSERIAL PRIMARY KEY,
 attempt_id UUID NOT NULL REFERENCES tbl_quiz_attempt(id),
 idempotency_key UUID NOT NULL,
 question_id UUID NOT NULL,
 payload_hash VARCHAR(64) NOT NULL,
 answer_version BIGINT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(attempt_id,idempotency_key)
);
CREATE INDEX idx_quiz_save_receipt_attempt ON tbl_quiz_save_receipt(attempt_id);
CREATE TABLE tbl_quiz_attempt_adjustment (
 id BIGSERIAL PRIMARY KEY,
 quiz_id UUID NOT NULL REFERENCES tbl_quiz(id),
 student_id UUID NOT NULL REFERENCES tbl_account(id),
 actor_id UUID NOT NULL REFERENCES tbl_account(id),
 action VARCHAR(10) NOT NULL CHECK(action IN('GRANT','RESET')),
 additional_attempts INTEGER NOT NULL CHECK(additional_attempts>=0),
 official_count INTEGER NOT NULL CHECK(official_count>=0),
 locked_count INTEGER NOT NULL CHECK(locked_count>=0),
 reason VARCHAR(1000) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_quiz_adjustment_quiz ON tbl_quiz_attempt_adjustment(quiz_id,student_id,id);
CREATE INDEX idx_quiz_adjustment_student ON tbl_quiz_attempt_adjustment(student_id);
CREATE INDEX idx_quiz_adjustment_actor ON tbl_quiz_attempt_adjustment(actor_id);
DO $$
DECLARE tbl TEXT; browser_role TEXT;
BEGIN
 FOREACH tbl IN ARRAY ARRAY['tbl_quiz','tbl_quiz_attempt','tbl_quiz_answer','tbl_quiz_save_receipt','tbl_quiz_attempt_adjustment'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',tbl);
  EXECUTE format('REVOKE ALL ON TABLE %I FROM PUBLIC',tbl);
  FOREACH browser_role IN ARRAY ARRAY['anon','authenticated'] LOOP
   IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=browser_role) THEN
    EXECUTE format('REVOKE ALL ON TABLE %I FROM %I',tbl,browser_role);
   END IF;
  END LOOP;
 END LOOP;
 REVOKE ALL ON SEQUENCE tbl_quiz_save_receipt_id_seq, tbl_quiz_attempt_adjustment_id_seq FROM PUBLIC;
 FOREACH browser_role IN ARRAY ARRAY['anon','authenticated'] LOOP
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=browser_role) THEN
   EXECUTE format('REVOKE ALL ON SEQUENCE tbl_quiz_save_receipt_id_seq, tbl_quiz_attempt_adjustment_id_seq FROM %I',browser_role);
  END IF;
 END LOOP;
END $$;
