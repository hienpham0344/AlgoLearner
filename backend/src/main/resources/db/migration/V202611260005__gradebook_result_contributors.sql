ALTER TABLE tbl_gradebook_student_result
    ADD COLUMN contributors JSONB,
    ADD CONSTRAINT ck_gradebook_result_contributors
        CHECK (contributors IS NULL OR jsonb_typeof(contributors) = 'array');
