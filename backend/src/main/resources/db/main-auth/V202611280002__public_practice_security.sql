-- Preserve legacy screenshots; new uploads store only a private object identifier.
ALTER TABLE tbl_problem_report ADD COLUMN image_file_id uuid;
CREATE INDEX idx_problem_report_status_created ON tbl_problem_report(status,created_at DESC);
CREATE INDEX idx_problem_report_reporter_created ON tbl_problem_report(reporter_id,created_at DESC);
CREATE INDEX idx_problem_report_open ON tbl_problem_report(reporter_id,problem_id)
    WHERE status IN ('OPEN','IN_REVIEW');
CREATE INDEX idx_judge_job_pending_owner ON tbl_judge_job(owner_id)
    WHERE mode='SUBMIT' AND status IN ('QUEUED','RUNNING');
