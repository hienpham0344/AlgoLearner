-- SRS 4.8 Classroom Practice. Ungraded and without deadline by design: no score or deadline columns,
-- and tbl_practice_completion must never feed the gradebook (4.10).
CREATE TABLE tbl_classroom_practice (
    id UUID PRIMARY KEY,
    classroom_id UUID NOT NULL REFERENCES tbl_classroom(id),
    -- No FK yet: tbl_lesson belongs to module 4.5. Add the constraint in that module's migration.
    lesson_id UUID,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    problem_type VARCHAR(20) NOT NULL CHECK (problem_type IN ('STANDARD_IO', 'FUNCTION_METHOD')),
    allowed_languages VARCHAR(40) NOT NULL,
    starter_code TEXT,
    time_limit_ms INTEGER NOT NULL DEFAULT 2000 CHECK (time_limit_ms BETWEEN 100 AND 5000),
    memory_limit_mb INTEGER NOT NULL DEFAULT 256 CHECK (memory_limit_mb BETWEEN 16 AND 512),
    status VARCHAR(20) NOT NULL CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    testcase_version INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_classroom_practice_classroom_id ON tbl_classroom_practice(classroom_id);
CREATE INDEX idx_classroom_practice_lesson_id ON tbl_classroom_practice(lesson_id);

-- Every testcase_version keeps its own rows, so a submission's snapshot is (practice_id, testcase_version).
CREATE TABLE tbl_practice_testcase (
    id UUID PRIMARY KEY,
    practice_id UUID NOT NULL REFERENCES tbl_classroom_practice(id),
    version INTEGER NOT NULL,
    input TEXT NOT NULL,
    expected_output TEXT NOT NULL,
    is_sample BOOLEAN NOT NULL,
    order_index INTEGER NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_practice_testcase_practice_version ON tbl_practice_testcase(practice_id, version);

CREATE TABLE tbl_practice_submission (
    id UUID PRIMARY KEY,
    practice_id UUID NOT NULL REFERENCES tbl_classroom_practice(id),
    student_id UUID NOT NULL REFERENCES tbl_account(id),
    testcase_version INTEGER NOT NULL,
    code TEXT NOT NULL,
    language VARCHAR(20) NOT NULL CHECK (language IN ('JAVA', 'PYTHON')),
    verdict VARCHAR(30) NOT NULL CHECK (verdict IN ('ACCEPTED', 'WRONG_ANSWER', 'COMPILATION_ERROR', 'RUNTIME_ERROR',
        'TIME_LIMIT_EXCEEDED', 'MEMORY_LIMIT_EXCEEDED', 'INTERNAL_ERROR')),
    -- FR-PRC-004: number of testcases of testcase_version that passed, next to how many there were.
    tests_passed INTEGER NOT NULL,
    total_tests INTEGER NOT NULL,
    runtime_ms INTEGER,
    memory_kb INTEGER,
    judge_details TEXT,
    rejudge_status VARCHAR(20) CHECK (rejudge_status IN ('PENDING_REJUDGE')),
    submitted_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_practice_submission_tests CHECK (tests_passed >= 0 AND tests_passed <= total_tests)
);
CREATE INDEX idx_practice_submission_practice_id ON tbl_practice_submission(practice_id);
CREATE INDEX idx_practice_submission_student_id ON tbl_practice_submission(student_id);

CREATE TABLE tbl_practice_completion (
    student_id UUID NOT NULL REFERENCES tbl_account(id),
    practice_id UUID NOT NULL REFERENCES tbl_classroom_practice(id),
    first_accepted_submission_id UUID NOT NULL REFERENCES tbl_practice_submission(id),
    completed_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (student_id, practice_id)
);
CREATE INDEX idx_practice_completion_practice_id ON tbl_practice_completion(practice_id);
CREATE INDEX idx_practice_completion_submission_id ON tbl_practice_completion(first_accepted_submission_id);

-- Same protection as every other application table (V202611110002): RLS stays enabled on tbl_*.
ALTER TABLE tbl_classroom_practice ENABLE ROW LEVEL SECURITY;
ALTER TABLE tbl_practice_testcase ENABLE ROW LEVEL SECURITY;
ALTER TABLE tbl_practice_submission ENABLE ROW LEVEL SECURITY;
ALTER TABLE tbl_practice_completion ENABLE ROW LEVEL SECURITY;
