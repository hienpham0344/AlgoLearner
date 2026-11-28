ALTER TABLE tbl_notification DROP CONSTRAINT tbl_notification_type_check;
ALTER TABLE tbl_notification ADD CONSTRAINT tbl_notification_type_check CHECK (
    type IN ('CLASS_INVITATION','ANNOUNCEMENT','ASSIGNMENT_PUBLISHED','ASSIGNMENT_REJUDGE',
             'ASSIGNMENT_DEADLINE','SYSTEM','PUBLIC_COURSE_REVIEW','GRADEBOOK_FINAL_CHANGED','PUBLIC_PROBLEM_REVIEW'));
ALTER TABLE tbl_public_problem ADD COLUMN review_status varchar(30);
UPDATE tbl_public_problem SET review_status = CASE status WHEN 'PUBLISHED' THEN 'APPROVED' ELSE status END;
ALTER TABLE tbl_public_problem ALTER COLUMN review_status SET NOT NULL,
    ALTER COLUMN review_status SET DEFAULT 'DRAFT',
    ADD CHECK (review_status IN ('DRAFT','PENDING_REVIEW','APPROVED','CHANGES_REQUESTED','REJECTED')),
    DROP CONSTRAINT tbl_public_problem_status_check;
UPDATE tbl_public_problem SET status='DRAFT' WHERE status IN ('REJECTED','PENDING_REVIEW');
ALTER TABLE tbl_public_problem ADD CHECK (status IN ('DRAFT','PUBLISHED','UNPUBLISHED','ARCHIVED'));
ALTER TABLE tbl_problem_revision ADD COLUMN difficulty varchar(8) NOT NULL DEFAULT 'EASY' CHECK(difficulty IN ('EASY','MEDIUM','HARD')),
    ADD COLUMN tags jsonb NOT NULL DEFAULT '[]', ADD COLUMN editorial text;
CREATE TABLE tbl_saved_problem (
    id uuid PRIMARY KEY, account_id uuid NOT NULL REFERENCES tbl_account(id), problem_id uuid NOT NULL REFERENCES tbl_public_problem(id),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(account_id,problem_id)
);
CREATE INDEX idx_saved_problem_problem ON tbl_saved_problem(problem_id);
CREATE TABLE tbl_problem_report (
    id uuid PRIMARY KEY, reporter_id uuid NOT NULL REFERENCES tbl_account(id), problem_id uuid NOT NULL REFERENCES tbl_public_problem(id),
    reason text NOT NULL CHECK(reason IN ('WRONG_TEST_CASE','INCORRECT_DESCRIPTION','INCORRECT_EDITORIAL','INAPPROPRIATE_CONTENT','OTHER')),
    description text NOT NULL, image_bytes bytea, image_type text,
    status text NOT NULL DEFAULT 'OPEN' CHECK(status IN ('OPEN','IN_REVIEW','RESOLVED','REJECTED')),
    created_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP, updated_at timestamptz NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_problem_report_problem ON tbl_problem_report(problem_id);
CREATE INDEX idx_problem_report_reporter ON tbl_problem_report(reporter_id);
ALTER TABLE tbl_saved_problem ENABLE ROW LEVEL SECURITY;
ALTER TABLE tbl_problem_report ENABLE ROW LEVEL SECURITY;

CREATE VIEW vw_public_submission_outcome AS
SELECT s.id,s.resource_id,s.owner_id,s.created_at,outcome.status
FROM tbl_submission s LEFT JOIN LATERAL (
    SELECT j.status FROM tbl_judge_job j WHERE j.submission_id=s.id AND j.mode='SUBMIT'
    AND j.status IN ('ACCEPTED','WRONG_ANSWER','COMPILE_ERROR','RUNTIME_ERROR','MEMORY_LIMIT_EXCEEDED','TIME_LIMIT_EXCEEDED','OUTPUT_LIMIT_EXCEEDED')
    ORDER BY generation DESC,created_at DESC,id LIMIT 1
) outcome ON true WHERE s.scope='PUBLIC_PROBLEM';
REVOKE ALL ON vw_public_submission_outcome,tbl_problem_report,tbl_saved_problem FROM PUBLIC;
DO $$ DECLARE role_name text; BEGIN
    FOREACH role_name IN ARRAY ARRAY['anon','authenticated'] LOOP
        IF EXISTS(SELECT FROM pg_roles WHERE rolname=role_name) THEN
            EXECUTE format('REVOKE ALL ON vw_public_submission_outcome,tbl_problem_report,tbl_saved_problem FROM %I',role_name);
        END IF;
    END LOOP;
END $$;

ALTER TABLE tbl_problem_revision DROP CONSTRAINT tbl_problem_revision_problem_id_fkey,
    ADD FOREIGN KEY(problem_id) REFERENCES tbl_public_problem(id) ON DELETE CASCADE;
CREATE OR REPLACE FUNCTION protect_problem_revision() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' AND NOT EXISTS(SELECT FROM tbl_public_problem WHERE id=OLD.problem_id) THEN RETURN OLD; END IF;
    RAISE EXCEPTION 'Problem revisions are immutable';
END $$;
