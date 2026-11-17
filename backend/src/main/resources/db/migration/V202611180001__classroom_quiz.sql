-- Quiz content and grading configuration are versioned by copying their JSON document
-- into each attempt. No cascade deletes: classroom/quiz history must be retained.
CREATE TABLE tbl_quiz (
    id UUID PRIMARY KEY,
    classroom_id UUID NOT NULL REFERENCES tbl_classroom(id),
    title VARCHAR(200) NOT NULL CHECK (length(trim(title)) > 0),
    configuration TEXT NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'CLOSED')),
    grades_published BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_quiz_classroom_id ON tbl_quiz(classroom_id);

CREATE TABLE tbl_quiz_attempt (
    id UUID PRIMARY KEY,
    quiz_id UUID NOT NULL REFERENCES tbl_quiz(id),
    student_id UUID NOT NULL REFERENCES tbl_account(id),
    attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
    snapshot TEXT NOT NULL,
    answers TEXT NOT NULL DEFAULT '{}',
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'SUBMITTED', 'EXPIRED')),
    started_at TIMESTAMPTZ NOT NULL,
    deadline TIMESTAMPTZ,
    submitted_at TIMESTAMPTZ,
    late BOOLEAN NOT NULL DEFAULT FALSE,
    score NUMERIC(12,4) CHECK (score >= 0 AND score <= 100),
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_quiz_attempt_number UNIQUE (quiz_id, student_id, attempt_number),
    CONSTRAINT ck_quiz_attempt_result CHECK (
        (status = 'SUBMITTED' AND submitted_at IS NOT NULL AND score IS NOT NULL)
        OR (status IN ('ACTIVE', 'EXPIRED') AND submitted_at IS NULL AND score IS NULL AND completed = FALSE)
    )
);
CREATE INDEX idx_quiz_attempt_student_id ON tbl_quiz_attempt(student_id);
CREATE INDEX idx_quiz_attempt_expiry ON tbl_quiz_attempt(status, deadline);
CREATE UNIQUE INDEX uq_quiz_active_attempt ON tbl_quiz_attempt(quiz_id, student_id) WHERE status = 'ACTIVE';

CREATE TABLE tbl_quiz_override (
    id UUID PRIMARY KEY,
    quiz_id UUID NOT NULL REFERENCES tbl_quiz(id),
    student_id UUID NOT NULL REFERENCES tbl_account(id),
    extra_attempts INTEGER NOT NULL DEFAULT 0 CHECK (extra_attempts >= 0),
    reset_at_count INTEGER NOT NULL DEFAULT 0 CHECK (reset_at_count >= 0),
    deadline TIMESTAMPTZ,
    time_limit_seconds INTEGER CHECK (time_limit_seconds > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_quiz_override_student UNIQUE (quiz_id, student_id)
);
CREATE INDEX idx_quiz_override_student_id ON tbl_quiz_override(student_id);

CREATE TABLE tbl_quiz_grade_override (
    id UUID PRIMARY KEY,
    quiz_id UUID NOT NULL REFERENCES tbl_quiz(id),
    student_id UUID NOT NULL REFERENCES tbl_account(id),
    actor_id UUID NOT NULL REFERENCES tbl_account(id),
    original_score NUMERIC(12,4) NOT NULL CHECK (original_score >= 0 AND original_score <= 100),
    final_score NUMERIC(12,4) NOT NULL CHECK (final_score >= 0 AND final_score <= 100),
    revision INTEGER NOT NULL CHECK (revision > 0),
    reason VARCHAR(2000) NOT NULL CHECK (length(trim(reason)) > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_quiz_grade_revision UNIQUE (quiz_id, student_id, revision)
);
CREATE INDEX idx_quiz_grade_override_history ON tbl_quiz_grade_override(quiz_id, student_id, created_at DESC);
CREATE INDEX idx_quiz_grade_override_student_id ON tbl_quiz_grade_override(student_id);
CREATE INDEX idx_quiz_grade_override_actor_id ON tbl_quiz_grade_override(actor_id);
