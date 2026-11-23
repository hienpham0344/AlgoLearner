ALTER TABLE tbl_quiz
    ADD COLUMN show_correct_answers BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN show_explanations BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN passing_score NUMERIC CHECK (graded AND passing_score BETWEEN 0 AND 100 OR passing_score IS NULL);

CREATE TABLE tbl_quiz_grade_override (
    id BIGSERIAL PRIMARY KEY,
    quiz_id UUID NOT NULL REFERENCES tbl_quiz(id),
    student_id UUID NOT NULL REFERENCES tbl_account(id),
    attempt_id UUID NOT NULL REFERENCES tbl_quiz_attempt(id),
    actor_id UUID NOT NULL REFERENCES tbl_account(id),
    original_score NUMERIC(7,4) NOT NULL CHECK (original_score BETWEEN 0 AND 100),
    final_score NUMERIC(7,4) NOT NULL CHECK (final_score BETWEEN 0 AND 100),
    reason VARCHAR(1000) NOT NULL CHECK (length(trim(reason)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_quiz_grade_override_history ON tbl_quiz_grade_override(quiz_id, student_id, id DESC);
CREATE INDEX idx_quiz_grade_override_student ON tbl_quiz_grade_override(student_id);
CREATE INDEX idx_quiz_grade_override_attempt ON tbl_quiz_grade_override(attempt_id);
CREATE INDEX idx_quiz_grade_override_actor ON tbl_quiz_grade_override(actor_id);

CREATE TABLE tbl_quiz_completion (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    quiz_id UUID NOT NULL REFERENCES tbl_quiz(id),
    student_id UUID NOT NULL REFERENCES tbl_account(id),
    completed BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (quiz_id, student_id)
);
CREATE INDEX idx_quiz_completion_student ON tbl_quiz_completion(student_id);
INSERT INTO tbl_quiz_completion(quiz_id, student_id, completed)
SELECT DISTINCT quiz_id, student_id, TRUE FROM tbl_quiz_attempt WHERE status='SUBMITTED';

ALTER TABLE tbl_quiz_completion ENABLE ROW LEVEL SECURITY;
ALTER TABLE tbl_quiz_grade_override ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE tbl_quiz_grade_override, tbl_quiz_completion FROM PUBLIC;
REVOKE ALL ON SEQUENCE tbl_quiz_grade_override_id_seq FROM PUBLIC;
DO $$
DECLARE browser_role TEXT;
BEGIN
    FOREACH browser_role IN ARRAY ARRAY['anon','authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname=browser_role) THEN
            EXECUTE format('REVOKE ALL ON TABLE tbl_quiz_grade_override, tbl_quiz_completion FROM %I', browser_role);
            EXECUTE format('REVOKE ALL ON SEQUENCE tbl_quiz_grade_override_id_seq FROM %I', browser_role);
        END IF;
    END LOOP;
END $$;
