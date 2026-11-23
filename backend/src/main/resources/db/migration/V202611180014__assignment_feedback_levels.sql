-- Preserve legacy aliases while exposing all three SRS feedback levels.
ALTER TABLE tbl_assignment DROP CONSTRAINT tbl_assignment_code_feedback_level_check;
ALTER TABLE tbl_assignment ADD CONSTRAINT tbl_assignment_code_feedback_level_check
    CHECK (code_feedback_level IN ('SUMMARY','DETAILED','PASS_FAIL','TESTCASE_STATUS','FULL_DETAILS'));
CREATE INDEX idx_assignment_scheduled_start ON tbl_assignment(start_time,id) WHERE status='SCHEDULED';
-- Each generation retains its immutable Assignment testcase context; existing trusted jobs retain their original context.
ALTER TABLE tbl_assignment_code_job ADD COLUMN revision_id UUID REFERENCES tbl_assignment_revision(id);
CREATE INDEX idx_assignment_code_job_revision ON tbl_assignment_code_job(revision_id);
UPDATE tbl_assignment_code_job j SET revision_id=a.revision_id FROM tbl_assignment_attempt a WHERE a.id=j.attempt_id;
CREATE FUNCTION protect_assignment_code_job_identity() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(OLD.id,OLD.attempt_id,OLD.question_id,OLD.job_id,OLD.generation,OLD.revision_id)
       IS DISTINCT FROM ROW(NEW.id,NEW.attempt_id,NEW.question_id,NEW.job_id,NEW.generation,NEW.revision_id) THEN
        RAISE EXCEPTION 'Assignment code job context is immutable';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_assignment_code_job_identity BEFORE UPDATE ON tbl_assignment_code_job
    FOR EACH ROW EXECUTE FUNCTION protect_assignment_code_job_identity();
