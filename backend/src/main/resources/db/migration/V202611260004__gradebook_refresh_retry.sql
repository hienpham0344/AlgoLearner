ALTER TABLE tbl_gradebook_refresh
    ADD COLUMN attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    ADD COLUMN next_attempt_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN last_error VARCHAR(120),
    ADD COLUMN failed_at TIMESTAMPTZ,
    ADD CONSTRAINT ck_gradebook_refresh_schedule CHECK ((failed_at IS NULL) = (next_attempt_at IS NOT NULL));

DROP INDEX idx_grade_refresh_order;
CREATE INDEX idx_grade_refresh_due
    ON tbl_gradebook_refresh(next_attempt_at, created_at, gradebook_id, student_id)
    WHERE failed_at IS NULL;
