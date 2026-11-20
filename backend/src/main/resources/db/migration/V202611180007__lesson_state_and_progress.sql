ALTER TABLE tbl_lesson ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tbl_lesson ADD COLUMN archived_from_status VARCHAR(30) CHECK (archived_from_status IN ('DRAFT','PUBLISHED'));
ALTER TABLE tbl_lesson ADD COLUMN rule_version BIGINT NOT NULL DEFAULT 0;
-- Keep all legacy links; fail upgrade clearly rather than deleting duplicates.
CREATE UNIQUE INDEX uq_lesson_item ON tbl_lesson_linked_item(lesson_id,item_type,item_id);
CREATE TABLE tbl_lesson_rule_history (
 id BIGSERIAL PRIMARY KEY,
 lesson_id UUID NOT NULL REFERENCES tbl_lesson(id),
 rule_version BIGINT NOT NULL,
 rule_json TEXT NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(lesson_id,rule_version)
);
CREATE TABLE tbl_lesson_completion_history (
 id BIGSERIAL PRIMARY KEY,
 lesson_id UUID NOT NULL REFERENCES tbl_lesson(id),
 student_id UUID NOT NULL REFERENCES tbl_account(id),
 rule_version BIGINT NOT NULL,
 source_fingerprint VARCHAR(64) NOT NULL,
 is_completed BOOLEAN NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(lesson_id,student_id,rule_version,source_fingerprint)
);
CREATE INDEX idx_lesson_completion_history_student ON tbl_lesson_completion_history(student_id);
DO $$
DECLARE tbl TEXT; browser_role TEXT;
BEGIN
 FOREACH tbl IN ARRAY ARRAY['tbl_lesson_rule_history','tbl_lesson_completion_history'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY',tbl);
  EXECUTE format('REVOKE ALL ON TABLE %I FROM PUBLIC',tbl);
  EXECUTE format('REVOKE ALL ON SEQUENCE %I FROM PUBLIC',tbl || '_id_seq');
  FOREACH browser_role IN ARRAY ARRAY['anon','authenticated'] LOOP
   IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=browser_role) THEN
    EXECUTE format('REVOKE ALL ON TABLE %I FROM %I',tbl,browser_role);
    EXECUTE format('REVOKE ALL ON SEQUENCE %I FROM %I',tbl || '_id_seq',browser_role);
   END IF;
  END LOOP;
 END LOOP;
END $$;

ALTER TABLE tbl_lesson_material ADD COLUMN stored_file_id UUID REFERENCES tbl_stored_file(id);
ALTER TABLE tbl_lesson_material ADD COLUMN material_state VARCHAR(30) NOT NULL DEFAULT 'NEEDS_REUPLOAD';
CREATE INDEX idx_lesson_material_stored_file ON tbl_lesson_material(stored_file_id);
