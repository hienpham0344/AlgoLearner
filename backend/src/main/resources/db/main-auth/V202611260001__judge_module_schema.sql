-- SRS 4.9 - Code Judge schema.
-- RUN executions are intentionally not persisted here. Every row in
-- tbl_judge_submission represents an official SUBMIT operation.

CREATE TABLE tbl_judge_problem (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES tbl_account(id) ON DELETE RESTRICT,
    context_type VARCHAR(30) NOT NULL
        CHECK (context_type IN ('PUBLIC_PROBLEM', 'CLASS_ASSIGNMENT', 'CLASS_PRACTICE')),
    context_id UUID NOT NULL,
    classroom_id UUID REFERENCES tbl_classroom(id) ON DELETE RESTRICT,
    title VARCHAR(255) NOT NULL,
    format VARCHAR(30) NOT NULL
        CHECK (format IN ('STANDARD_IO', 'FUNCTION_METHOD')),
    time_limit_ms INTEGER NOT NULL DEFAULT 2000
        CHECK (time_limit_ms BETWEEN 100 AND 5000),
    memory_limit_mb INTEGER NOT NULL DEFAULT 256
        CHECK (memory_limit_mb BETWEEN 16 AND 512),
    feedback_level VARCHAR(30) NOT NULL DEFAULT 'AGGREGATE'
        CHECK (feedback_level IN ('AGGREGATE', 'TEST_STATUS', 'DETAILED')),
    enabled_languages VARCHAR(32) NOT NULL DEFAULT 'JAVA,PYTHON'
        CHECK (enabled_languages IN ('JAVA', 'PYTHON', 'JAVA,PYTHON')),
    starter_code_java TEXT,
    starter_code_python TEXT,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_judge_problem_context UNIQUE (context_type, context_id),
    CONSTRAINT ck_judge_problem_classroom_context CHECK (
        (context_type = 'PUBLIC_PROBLEM' AND classroom_id IS NULL)
        OR
        (context_type IN ('CLASS_ASSIGNMENT', 'CLASS_PRACTICE') AND classroom_id IS NOT NULL)
    ),
    CONSTRAINT ck_judge_problem_title_not_blank CHECK (btrim(title) <> '')
);
CREATE INDEX idx_judge_problem_owner_id ON tbl_judge_problem(owner_id);
CREATE INDEX idx_judge_problem_classroom_id ON tbl_judge_problem(classroom_id);

CREATE TABLE tbl_judge_test_case_version (
    id UUID PRIMARY KEY,
    problem_id UUID NOT NULL REFERENCES tbl_judge_problem(id) ON DELETE RESTRICT,
    version_no INTEGER NOT NULL CHECK (version_no > 0),
    active BOOLEAN NOT NULL DEFAULT FALSE,
    checksum_sha256 VARCHAR(64),
    created_by UUID NOT NULL REFERENCES tbl_account(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_judge_test_case_version UNIQUE (problem_id, version_no),
    CONSTRAINT ck_judge_test_case_version_checksum
        CHECK (checksum_sha256 IS NULL OR checksum_sha256 ~ '^[0-9a-fA-F]{64}$')
);
CREATE INDEX idx_judge_test_case_version_created_by
    ON tbl_judge_test_case_version(created_by);
CREATE UNIQUE INDEX uq_judge_test_case_version_active
    ON tbl_judge_test_case_version(problem_id)
    WHERE active;

CREATE TABLE tbl_judge_test_case (
    id UUID PRIMARY KEY,
    version_id UUID NOT NULL REFERENCES tbl_judge_test_case_version(id) ON DELETE RESTRICT,
    ordinal INTEGER NOT NULL CHECK (ordinal > 0),
    visibility VARCHAR(20) NOT NULL CHECK (visibility IN ('SAMPLE', 'HIDDEN')),
    input_data TEXT NOT NULL,
    expected_output TEXT NOT NULL,
    weight NUMERIC(8, 4) NOT NULL DEFAULT 1.0000 CHECK (weight > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_judge_test_case_ordinal UNIQUE (version_id, ordinal)
);

CREATE TABLE tbl_judge_submission (
    id UUID PRIMARY KEY,
    problem_id UUID NOT NULL REFERENCES tbl_judge_problem(id) ON DELETE RESTRICT,
    submitter_id UUID NOT NULL REFERENCES tbl_account(id) ON DELETE RESTRICT,
    test_case_version_id UUID NOT NULL REFERENCES tbl_judge_test_case_version(id) ON DELETE RESTRICT,
    language VARCHAR(20) NOT NULL CHECK (language IN ('JAVA', 'PYTHON')),
    source_code TEXT NOT NULL,
    source_code_sha256 VARCHAR(64),
    status VARCHAR(40) NOT NULL DEFAULT 'QUEUED'
        CHECK (status IN (
            'PENDING', 'QUEUED', 'JUDGING', 'ACCEPTED', 'WRONG_ANSWER',
            'COMPILATION_ERROR', 'RUNTIME_ERROR', 'TIME_LIMIT_EXCEEDED',
            'MEMORY_LIMIT_EXCEEDED', 'JUDGE_ERROR', 'CANCELLED'
        )),
    official BOOLEAN NOT NULL DEFAULT TRUE CHECK (official),
    time_limit_ms INTEGER NOT NULL DEFAULT 2000 CHECK (time_limit_ms BETWEEN 100 AND 5000),
    memory_limit_mb INTEGER NOT NULL DEFAULT 256 CHECK (memory_limit_mb BETWEEN 16 AND 512),
    feedback_level VARCHAR(30) NOT NULL DEFAULT 'AGGREGATE'
        CHECK (feedback_level IN ('AGGREGATE', 'TEST_STATUS', 'DETAILED')),
    execution_time_ms BIGINT CHECK (execution_time_ms IS NULL OR execution_time_ms >= 0),
    memory_used_kb BIGINT CHECK (memory_used_kb IS NULL OR memory_used_kb >= 0),
    compiler_output TEXT,
    error_message TEXT,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_judge_submission_source_not_blank CHECK (btrim(source_code) <> ''),
    CONSTRAINT ck_judge_submission_source_checksum
        CHECK (source_code_sha256 IS NULL OR source_code_sha256 ~ '^[0-9a-fA-F]{64}$')
);
CREATE INDEX idx_judge_submission_problem_created
    ON tbl_judge_submission(problem_id, created_at DESC);
CREATE INDEX idx_judge_submission_submitter_created
    ON tbl_judge_submission(submitter_id, created_at DESC);
CREATE INDEX idx_judge_submission_test_case_version_id
    ON tbl_judge_submission(test_case_version_id);
CREATE INDEX idx_judge_submission_status_created
    ON tbl_judge_submission(status, created_at)
    WHERE status IN ('PENDING', 'QUEUED', 'JUDGING');

-- An evaluation is an immutable judge attempt. The first attempt is INITIAL;
-- later attempts are REJUDGE. A failed rejudge never replaces the prior
-- current official evaluation.
CREATE TABLE tbl_submission_evaluation (
    id UUID PRIMARY KEY,
    submission_id UUID NOT NULL REFERENCES tbl_judge_submission(id) ON DELETE RESTRICT,
    test_case_version_id UUID NOT NULL REFERENCES tbl_judge_test_case_version(id) ON DELETE RESTRICT,
    attempt_no INTEGER NOT NULL CHECK (attempt_no > 0),
    trigger_type VARCHAR(20) NOT NULL CHECK (trigger_type IN ('INITIAL', 'REJUDGE')),
    requested_by UUID NOT NULL REFERENCES tbl_account(id) ON DELETE RESTRICT,
    status VARCHAR(40) NOT NULL
        CHECK (status IN (
            'PENDING', 'QUEUED', 'JUDGING', 'ACCEPTED', 'WRONG_ANSWER',
            'COMPILATION_ERROR', 'RUNTIME_ERROR', 'TIME_LIMIT_EXCEEDED',
            'MEMORY_LIMIT_EXCEEDED', 'JUDGE_ERROR', 'CANCELLED'
        )),
    current_official BOOLEAN NOT NULL DEFAULT FALSE,
    judge_provider VARCHAR(100),
    execution_time_ms BIGINT CHECK (execution_time_ms IS NULL OR execution_time_ms >= 0),
    memory_used_kb BIGINT CHECK (memory_used_kb IS NULL OR memory_used_kb >= 0),
    compiler_output TEXT,
    error_message TEXT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_submission_evaluation_attempt UNIQUE (submission_id, attempt_no)
);
CREATE INDEX idx_submission_evaluation_test_case_version_id
    ON tbl_submission_evaluation(test_case_version_id);
CREATE INDEX idx_submission_evaluation_requested_by
    ON tbl_submission_evaluation(requested_by);
CREATE UNIQUE INDEX uq_submission_evaluation_current_official
    ON tbl_submission_evaluation(submission_id)
    WHERE current_official;

-- Snapshot rows copy the exact input/output used by each evaluation. They are
-- not cascaded when an author edits or retires a test-case version.
CREATE TABLE tbl_submission_test_case_snapshot (
    id UUID PRIMARY KEY,
    evaluation_id UUID NOT NULL REFERENCES tbl_submission_evaluation(id) ON DELETE RESTRICT,
    source_test_case_id UUID REFERENCES tbl_judge_test_case(id) ON DELETE SET NULL,
    ordinal INTEGER NOT NULL CHECK (ordinal > 0),
    visibility VARCHAR(20) NOT NULL CHECK (visibility IN ('SAMPLE', 'HIDDEN')),
    input_data TEXT NOT NULL,
    expected_output TEXT NOT NULL,
    weight NUMERIC(8, 4) NOT NULL CHECK (weight > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_submission_snapshot_ordinal UNIQUE (evaluation_id, ordinal)
);
CREATE INDEX idx_submission_snapshot_source_test_case_id
    ON tbl_submission_test_case_snapshot(source_test_case_id);

CREATE TABLE tbl_submission_test_result (
    id UUID PRIMARY KEY,
    evaluation_id UUID NOT NULL REFERENCES tbl_submission_evaluation(id) ON DELETE RESTRICT,
    snapshot_id UUID NOT NULL REFERENCES tbl_submission_test_case_snapshot(id) ON DELETE RESTRICT,
    status VARCHAR(40) NOT NULL
        CHECK (status IN (
            'ACCEPTED', 'WRONG_ANSWER', 'COMPILATION_ERROR', 'RUNTIME_ERROR',
            'TIME_LIMIT_EXCEEDED', 'MEMORY_LIMIT_EXCEEDED', 'JUDGE_ERROR'
        )),
    passed BOOLEAN NOT NULL,
    actual_output TEXT,
    stderr_output TEXT,
    execution_time_ms BIGINT CHECK (execution_time_ms IS NULL OR execution_time_ms >= 0),
    memory_used_kb BIGINT CHECK (memory_used_kb IS NULL OR memory_used_kb >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_submission_test_result_snapshot UNIQUE (snapshot_id)
);
CREATE INDEX idx_submission_test_result_evaluation_id
    ON tbl_submission_test_result(evaluation_id);

CREATE TABLE tbl_rejudge_history (
    id BIGSERIAL PRIMARY KEY,
    submission_id UUID NOT NULL REFERENCES tbl_judge_submission(id) ON DELETE RESTRICT,
    requested_by UUID NOT NULL REFERENCES tbl_account(id) ON DELETE RESTRICT,
    previous_evaluation_id UUID REFERENCES tbl_submission_evaluation(id) ON DELETE RESTRICT,
    new_evaluation_id UUID REFERENCES tbl_submission_evaluation(id) ON DELETE RESTRICT,
    old_status VARCHAR(40) NOT NULL
        CHECK (old_status IN (
            'PENDING', 'QUEUED', 'JUDGING', 'ACCEPTED', 'WRONG_ANSWER',
            'COMPILATION_ERROR', 'RUNTIME_ERROR', 'TIME_LIMIT_EXCEEDED',
            'MEMORY_LIMIT_EXCEEDED', 'JUDGE_ERROR', 'CANCELLED'
        )),
    new_status VARCHAR(40)
        CHECK (new_status IS NULL OR new_status IN (
            'PENDING', 'QUEUED', 'JUDGING', 'ACCEPTED', 'WRONG_ANSWER',
            'COMPILATION_ERROR', 'RUNTIME_ERROR', 'TIME_LIMIT_EXCEEDED',
            'MEMORY_LIMIT_EXCEEDED', 'JUDGE_ERROR', 'CANCELLED'
        )),
    operation_status VARCHAR(20) NOT NULL DEFAULT 'QUEUED'
        CHECK (operation_status IN ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED')),
    reason VARCHAR(1000),
    failure_message TEXT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_rejudge_history_submission_requested
    ON tbl_rejudge_history(submission_id, requested_at DESC);
CREATE INDEX idx_rejudge_history_requested_by
    ON tbl_rejudge_history(requested_by);
CREATE INDEX idx_rejudge_history_previous_evaluation_id
    ON tbl_rejudge_history(previous_evaluation_id);
CREATE INDEX idx_rejudge_history_new_evaluation_id
    ON tbl_rejudge_history(new_evaluation_id);

ALTER TABLE tbl_judge_problem ENABLE ROW LEVEL SECURITY;
ALTER TABLE tbl_judge_test_case_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE tbl_judge_test_case ENABLE ROW LEVEL SECURITY;
ALTER TABLE tbl_judge_submission ENABLE ROW LEVEL SECURITY;
ALTER TABLE tbl_submission_evaluation ENABLE ROW LEVEL SECURITY;
ALTER TABLE tbl_submission_test_case_snapshot ENABLE ROW LEVEL SECURITY;
ALTER TABLE tbl_submission_test_result ENABLE ROW LEVEL SECURITY;
ALTER TABLE tbl_rejudge_history ENABLE ROW LEVEL SECURITY;

REVOKE ALL ON
    tbl_judge_problem,
    tbl_judge_test_case_version,
    tbl_judge_test_case,
    tbl_judge_submission,
    tbl_submission_evaluation,
    tbl_submission_test_case_snapshot,
    tbl_submission_test_result,
    tbl_rejudge_history
FROM PUBLIC;

DO $$ DECLARE r TEXT; BEGIN
    FOREACH r IN ARRAY ARRAY['anon', 'authenticated'] LOOP
        IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
            EXECUTE format(
                'REVOKE ALL ON tbl_judge_problem, tbl_judge_test_case_version, tbl_judge_test_case, tbl_judge_submission, tbl_submission_evaluation, tbl_submission_test_case_snapshot, tbl_submission_test_result, tbl_rejudge_history FROM %I',
                r
            );
        END IF;
    END LOOP;
END $$;
