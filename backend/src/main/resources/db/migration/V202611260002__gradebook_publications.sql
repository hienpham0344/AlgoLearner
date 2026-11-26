CREATE TABLE tbl_gradebook_publication (
    id UUID PRIMARY KEY,
    gradebook_id UUID NOT NULL REFERENCES tbl_gradebook(id),
    scheme_revision_id UUID NOT NULL,
    actor_id UUID NOT NULL REFERENCES tbl_account(id),
    scope VARCHAR(10) NOT NULL CHECK (scope IN ('STUDENT','CLASS')),
    target_student_id UUID REFERENCES tbl_account(id),
    idempotency_key VARCHAR(128) NOT NULL,
    expected_version BIGINT NOT NULL,
    applied_version BIGINT NOT NULL,
    published_count INTEGER NOT NULL CHECK (published_count > 0),
    ineligible_count INTEGER NOT NULL CHECK (ineligible_count >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK ((scope='STUDENT') = (target_student_id IS NOT NULL)),
    UNIQUE (gradebook_id,idempotency_key),
    UNIQUE (id,gradebook_id),
    FOREIGN KEY (scheme_revision_id,gradebook_id) REFERENCES tbl_grade_scheme_revision(id,gradebook_id)
);
CREATE INDEX idx_grade_publication_scheme ON tbl_gradebook_publication(scheme_revision_id,gradebook_id);
CREATE INDEX idx_grade_publication_actor ON tbl_gradebook_publication(actor_id);
CREATE INDEX idx_grade_publication_target ON tbl_gradebook_publication(target_student_id);

CREATE TABLE tbl_gradebook_student_result (
    id UUID PRIMARY KEY,
    gradebook_id UUID NOT NULL,
    student_id UUID NOT NULL REFERENCES tbl_account(id),
    publication_id UUID NOT NULL,
    scheme_revision_id UUID NOT NULL,
    overall JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (id,gradebook_id,student_id),
    FOREIGN KEY (publication_id,gradebook_id) REFERENCES tbl_gradebook_publication(id,gradebook_id),
    FOREIGN KEY (scheme_revision_id,gradebook_id) REFERENCES tbl_grade_scheme_revision(id,gradebook_id)
);
CREATE INDEX idx_grade_student_result_publication ON tbl_gradebook_student_result(publication_id,gradebook_id);
CREATE INDEX idx_grade_student_result_scheme ON tbl_gradebook_student_result(scheme_revision_id,gradebook_id);
CREATE INDEX idx_grade_student_result_student ON tbl_gradebook_student_result(student_id);

CREATE TABLE tbl_gradebook_published_student (
    gradebook_id UUID NOT NULL REFERENCES tbl_gradebook(id),
    student_id UUID NOT NULL REFERENCES tbl_account(id),
    result_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (gradebook_id,student_id),
    FOREIGN KEY (result_id,gradebook_id,student_id) REFERENCES tbl_gradebook_student_result(id,gradebook_id,student_id)
);
CREATE INDEX idx_grade_published_student_result ON tbl_gradebook_published_student(result_id,gradebook_id,student_id);
CREATE INDEX idx_grade_published_student_student ON tbl_gradebook_published_student(student_id);

CREATE TABLE tbl_gradebook_refresh (
    gradebook_id UUID NOT NULL REFERENCES tbl_gradebook(id),
    student_id UUID NOT NULL REFERENCES tbl_account(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (gradebook_id,student_id)
);
CREATE INDEX idx_grade_refresh_order ON tbl_gradebook_refresh(created_at,gradebook_id,student_id);
CREATE INDEX idx_grade_refresh_student ON tbl_gradebook_refresh(student_id);

ALTER TABLE tbl_gradebook_publication ENABLE ROW LEVEL SECURITY;
ALTER TABLE tbl_gradebook_student_result ENABLE ROW LEVEL SECURITY;
ALTER TABLE tbl_gradebook_published_student ENABLE ROW LEVEL SECURITY;
ALTER TABLE tbl_gradebook_refresh ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON tbl_gradebook_publication,tbl_gradebook_student_result,tbl_gradebook_published_student,tbl_gradebook_refresh FROM PUBLIC;
DO $$
DECLARE browser_role TEXT;
BEGIN
    FOREACH browser_role IN ARRAY ARRAY['anon','authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname=browser_role) THEN
            EXECUTE format('REVOKE ALL ON tbl_gradebook_publication,tbl_gradebook_student_result,tbl_gradebook_published_student,tbl_gradebook_refresh FROM %I', browser_role);
        END IF;
    END LOOP;
END $$;
