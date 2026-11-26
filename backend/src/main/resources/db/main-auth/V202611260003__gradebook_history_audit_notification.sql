CREATE TABLE tbl_gradebook_history (
    id BIGSERIAL PRIMARY KEY,
    gradebook_id UUID NOT NULL REFERENCES tbl_gradebook(id),
    student_id UUID REFERENCES tbl_account(id),
    actor_id UUID REFERENCES tbl_account(id),
    scheme_revision_id UUID NOT NULL,
    action VARCHAR(40) NOT NULL CHECK (action IN ('SCHEME_APPLIED','SCHEME_RECALCULATED','SOURCE_RECALCULATED')),
    reason VARCHAR(500),
    previous_value JSONB,
    current_value JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (scheme_revision_id,gradebook_id) REFERENCES tbl_grade_scheme_revision(id,gradebook_id)
);
CREATE INDEX idx_gradebook_history_page ON tbl_gradebook_history(gradebook_id,id DESC);
CREATE INDEX idx_gradebook_history_student ON tbl_gradebook_history(student_id);
CREATE INDEX idx_gradebook_history_actor ON tbl_gradebook_history(actor_id);
CREATE INDEX idx_gradebook_history_scheme ON tbl_gradebook_history(scheme_revision_id,gradebook_id);

ALTER TABLE tbl_audit_log ADD COLUMN classroom_id UUID REFERENCES tbl_classroom(id) ON DELETE SET NULL;
ALTER TABLE tbl_audit_log ADD COLUMN metadata JSONB;
CREATE INDEX idx_audit_log_classroom ON tbl_audit_log(classroom_id);

ALTER TABLE tbl_notification DROP CONSTRAINT tbl_notification_type_check;
ALTER TABLE tbl_notification ADD CONSTRAINT tbl_notification_type_check
    CHECK (type IN ('CLASS_INVITATION','ANNOUNCEMENT','ASSIGNMENT_PUBLISHED','ASSIGNMENT_REJUDGE',
                    'ASSIGNMENT_DEADLINE','SYSTEM','PUBLIC_COURSE_REVIEW','GRADEBOOK_FINAL_CHANGED'));
ALTER TABLE tbl_notification ADD COLUMN gradebook_classroom_id UUID REFERENCES tbl_classroom(id);
ALTER TABLE tbl_notification ADD COLUMN gradebook_result_id UUID REFERENCES tbl_gradebook_student_result(id);
ALTER TABLE tbl_notification ADD CONSTRAINT ck_notification_gradebook_target CHECK (
    (type='GRADEBOOK_FINAL_CHANGED' AND gradebook_classroom_id IS NOT NULL AND gradebook_result_id IS NOT NULL)
    OR (type<>'GRADEBOOK_FINAL_CHANGED' AND gradebook_classroom_id IS NULL AND gradebook_result_id IS NULL)
);
CREATE UNIQUE INDEX uq_notification_gradebook_result ON tbl_notification(recipient_id,gradebook_result_id)
    WHERE gradebook_result_id IS NOT NULL;
CREATE INDEX idx_notification_gradebook_classroom ON tbl_notification(gradebook_classroom_id);
CREATE INDEX idx_notification_gradebook_result ON tbl_notification(gradebook_result_id);

ALTER TABLE tbl_gradebook_history ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON TABLE tbl_gradebook_history FROM PUBLIC;
DO $$
DECLARE browser_role TEXT;
BEGIN
    FOREACH browser_role IN ARRAY ARRAY['anon','authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = browser_role) THEN
            EXECUTE format('REVOKE ALL ON TABLE tbl_gradebook_history FROM %I', browser_role);
        END IF;
    END LOOP;
END $$;
