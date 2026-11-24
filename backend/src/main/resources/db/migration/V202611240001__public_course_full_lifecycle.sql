-- SRS 4.11 Public Course: separate Review (revision) from Publication, rich lessons,
-- public quizzes, per-learner progress and preserved learning history.

-- 1. Course metadata and publication lifecycle ------------------------------------------
ALTER TABLE tbl_public_course
    ADD COLUMN level VARCHAR(20) CHECK (level IN ('BEGINNER','INTERMEDIATE','ADVANCED')),
    ADD COLUMN topic VARCHAR(80),
    ADD COLUMN estimated_minutes INTEGER CHECK (estimated_minutes IS NULL OR estimated_minutes BETWEEN 1 AND 100000),
    ADD COLUMN unpublish_policy VARCHAR(30) CHECK (unpublish_policy IN ('ENROLLED_CONTINUE','LOCK_ALL')),
    ADD COLUMN published_at TIMESTAMPTZ,
    ADD COLUMN content_updated_at TIMESTAMPTZ;

-- 2. Lessons: typed content blocks, preview, duration, soft removal ----------------------
ALTER TABLE tbl_public_course_unit
    ALTER COLUMN content DROP NOT NULL,
    ADD COLUMN content_blocks JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD COLUMN is_preview BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN estimated_minutes INTEGER CHECK (estimated_minutes IS NULL OR estimated_minutes BETWEEN 1 AND 100000),
    ADD COLUMN prerequisite_note VARCHAR(500),
    ADD COLUMN removed_at TIMESTAMPTZ;
UPDATE tbl_public_course_unit
   SET content_blocks = jsonb_build_array(jsonb_build_object('type','TEXT','text',content))
 WHERE content IS NOT NULL;
ALTER TABLE tbl_public_course_unit DROP CONSTRAINT IF EXISTS tbl_public_course_unit_course_id_display_order_key;
CREATE INDEX idx_public_course_unit_course ON tbl_public_course_unit(course_id, display_order);

-- 3. Contribution revisions (Review status lives here, never on the course) --------------
CREATE TABLE tbl_public_course_revision (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES tbl_public_course(id) ON DELETE RESTRICT,
    author_id UUID NOT NULL REFERENCES tbl_account(id) ON DELETE RESTRICT,
    reviewer_id UUID REFERENCES tbl_account(id) ON DELETE RESTRICT,
    revision_no INTEGER NOT NULL CHECK (revision_no >= 1),
    status VARCHAR(30) NOT NULL CHECK (status IN ('DRAFT','PENDING_REVIEW','APPROVED','CHANGES_REQUESTED','REJECTED')),
    snapshot JSONB NOT NULL,
    reason VARCHAR(1000),
    submitted_at TIMESTAMPTZ,
    reviewed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (course_id, revision_no),
    CHECK (status NOT IN ('CHANGES_REQUESTED','REJECTED') OR (reason IS NOT NULL AND length(btrim(reason)) > 0))
);
CREATE UNIQUE INDEX uq_public_course_open_revision ON tbl_public_course_revision(course_id)
    WHERE status IN ('DRAFT','PENDING_REVIEW','CHANGES_REQUESTED');
CREATE INDEX idx_public_course_revision_author ON tbl_public_course_revision(author_id, status);
CREATE INDEX idx_public_course_revision_reviewer ON tbl_public_course_revision(reviewer_id);
CREATE INDEX idx_public_course_revision_queue ON tbl_public_course_revision(status, submitted_at);

-- Legacy rows: the old single status mixed review and publication.
INSERT INTO tbl_public_course_revision(id, course_id, author_id, revision_no, status, snapshot, reason,
                                       submitted_at, reviewed_at, created_at, updated_at)
SELECT gen_random_uuid(), c.id, c.author_id, 1,
       CASE c.status WHEN 'PUBLISHED' THEN 'APPROVED' WHEN 'PENDING_REVIEW' THEN 'PENDING_REVIEW'
                     WHEN 'REJECTED' THEN 'REJECTED' ELSE 'DRAFT' END,
       jsonb_build_object('title', c.title, 'description', c.description, 'level', NULL, 'topic', NULL,
           'estimatedMinutes', NULL, 'quizzes', '[]'::jsonb,
           'lessons', COALESCE((SELECT jsonb_agg(jsonb_build_object('id', u.id, 'title', u.title,
                   'estimatedMinutes', NULL, 'prerequisiteNote', NULL, 'preview', FALSE,
                   'blocks', u.content_blocks) ORDER BY u.display_order)
               FROM tbl_public_course_unit u WHERE u.course_id = c.id), '[]'::jsonb)),
       CASE WHEN c.status = 'REJECTED' THEN COALESCE(NULLIF(btrim(c.review_reason), ''), 'Rejected before migration') END,
       CASE WHEN c.status <> 'DRAFT' THEN c.updated_at END,
       CASE WHEN c.status IN ('PUBLISHED','REJECTED') THEN c.updated_at END,
       c.created_at, c.updated_at
  FROM tbl_public_course c;

UPDATE tbl_public_course SET published_at = updated_at WHERE status = 'PUBLISHED';
UPDATE tbl_public_course SET status = 'DRAFT' WHERE status IN ('PENDING_REVIEW','REJECTED');
ALTER TABLE tbl_public_course DROP CONSTRAINT IF EXISTS tbl_public_course_status_check;
ALTER TABLE tbl_public_course ADD CONSTRAINT tbl_public_course_status_check
    CHECK (status IN ('DRAFT','PUBLISHED','UNPUBLISHED','ARCHIVED'));
ALTER TABLE tbl_public_course ADD CONSTRAINT tbl_public_course_unpublished_policy_required
    CHECK (status <> 'UNPUBLISHED' OR unpublish_policy IS NOT NULL);
ALTER TABLE tbl_public_course DROP COLUMN review_reason;
CREATE INDEX idx_public_course_catalog ON tbl_public_course(status, level, topic);

-- 4. Public course quizzes --------------------------------------------------------------
CREATE TABLE tbl_public_course_quiz (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL REFERENCES tbl_public_course(id) ON DELETE RESTRICT,
    lesson_id UUID REFERENCES tbl_public_course_unit(id) ON DELETE RESTRICT,
    title VARCHAR(255) NOT NULL,
    mandatory BOOLEAN NOT NULL DEFAULT TRUE,
    completion_rule VARCHAR(20) NOT NULL CHECK (completion_rule IN ('SUBMIT','PASSING_SCORE')),
    passing_score NUMERIC(5,2) CHECK (passing_score IS NULL OR passing_score BETWEEN 0 AND 100),
    max_attempts INTEGER CHECK (max_attempts IS NULL OR max_attempts BETWEEN 1 AND 100),
    time_limit_seconds INTEGER CHECK (time_limit_seconds IS NULL OR time_limit_seconds BETWEEN 60 AND 86400),
    answer_visibility VARCHAR(20) NOT NULL CHECK (answer_visibility IN ('NEVER','AFTER_SUBMIT','AFTER_PASSED')),
    questions JSONB NOT NULL,
    display_order INTEGER NOT NULL CHECK (display_order >= 0),
    removed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CHECK (completion_rule <> 'PASSING_SCORE' OR passing_score IS NOT NULL)
);
CREATE INDEX idx_public_course_quiz_course ON tbl_public_course_quiz(course_id, display_order);
CREATE INDEX idx_public_course_quiz_lesson ON tbl_public_course_quiz(lesson_id);

-- 5. Enrollment progress and history -----------------------------------------------------
ALTER TABLE tbl_course_enrollment
    ADD COLUMN progress_status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED'
        CHECK (progress_status IN ('NOT_STARTED','IN_PROGRESS','COMPLETED')),
    ADD COLUMN progress_percent NUMERIC(5,2) NOT NULL DEFAULT 0 CHECK (progress_percent BETWEEN 0 AND 100),
    ADD COLUMN started_at TIMESTAMPTZ,
    ADD COLUMN completed_at TIMESTAMPTZ,
    ADD COLUMN has_update BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN unenrolled_at TIMESTAMPTZ,
    ADD COLUMN last_activity_at TIMESTAMPTZ,
    ADD CONSTRAINT tbl_course_enrollment_completed_check CHECK (progress_status <> 'COMPLETED' OR completed_at IS NOT NULL);
CREATE INDEX idx_course_enrollment_course ON tbl_course_enrollment(course_id);
CREATE INDEX idx_course_completion_enrollment ON tbl_course_unit_completion(enrollment_id);

-- Backfill progress for enrollments created by the MVP flow.
UPDATE tbl_course_enrollment e SET
    progress_percent = p.percent,
    progress_status = CASE WHEN p.done = 0 THEN 'NOT_STARTED' WHEN p.done >= p.total THEN 'COMPLETED' ELSE 'IN_PROGRESS' END,
    started_at = CASE WHEN p.done > 0 THEN p.first_done END,
    completed_at = CASE WHEN p.done > 0 AND p.done >= p.total THEN p.last_done END,
    last_activity_at = p.last_done
FROM (SELECT e2.id,
             (SELECT count(*) FROM tbl_public_course_unit u WHERE u.course_id = e2.course_id) AS total,
             count(c.id) AS done, min(c.created_at) AS first_done, max(c.created_at) AS last_done,
             CASE WHEN (SELECT count(*) FROM tbl_public_course_unit u WHERE u.course_id = e2.course_id) = 0 THEN 0
                  ELSE round(100.0 * count(c.id) / (SELECT count(*) FROM tbl_public_course_unit u WHERE u.course_id = e2.course_id), 2) END AS percent
        FROM tbl_course_enrollment e2 LEFT JOIN tbl_course_unit_completion c ON c.enrollment_id = e2.id
       GROUP BY e2.id, e2.course_id) p
WHERE p.id = e.id;

CREATE TABLE tbl_public_course_quiz_attempt (
    id UUID PRIMARY KEY,
    quiz_id UUID NOT NULL REFERENCES tbl_public_course_quiz(id) ON DELETE RESTRICT,
    enrollment_id UUID NOT NULL REFERENCES tbl_course_enrollment(id) ON DELETE RESTRICT,
    attempt_no INTEGER NOT NULL CHECK (attempt_no >= 1),
    status VARCHAR(20) NOT NULL CHECK (status IN ('IN_PROGRESS','SUBMITTED')),
    started_at TIMESTAMPTZ NOT NULL,
    deadline_at TIMESTAMPTZ,
    submitted_at TIMESTAMPTZ,
    score NUMERIC(7,2) CHECK (score IS NULL OR score BETWEEN 0 AND 100),
    passed BOOLEAN,
    quiz_snapshot JSONB NOT NULL,
    answers JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (quiz_id, enrollment_id, attempt_no),
    CHECK (status <> 'SUBMITTED' OR (submitted_at IS NOT NULL AND score IS NOT NULL AND passed IS NOT NULL))
);
CREATE UNIQUE INDEX uq_public_course_quiz_open_attempt ON tbl_public_course_quiz_attempt(quiz_id, enrollment_id)
    WHERE status = 'IN_PROGRESS';
CREATE INDEX idx_public_course_quiz_attempt_enrollment ON tbl_public_course_quiz_attempt(enrollment_id);

CREATE TABLE tbl_course_enrollment_event (
    id BIGSERIAL PRIMARY KEY,
    enrollment_id UUID NOT NULL REFERENCES tbl_course_enrollment(id) ON DELETE RESTRICT,
    event VARCHAR(30) NOT NULL CHECK (event IN ('ENROLLED','UNENROLLED','STARTED','LESSON_COMPLETED',
        'QUIZ_SUBMITTED','COMPLETED','CONTENT_UPDATED')),
    detail VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_course_enrollment_event_enrollment ON tbl_course_enrollment_event(enrollment_id, created_at);

-- 6. Review notifications ----------------------------------------------------------------
ALTER TABLE tbl_notification DROP CONSTRAINT IF EXISTS tbl_notification_type_check;
ALTER TABLE tbl_notification ADD CONSTRAINT tbl_notification_type_check
    CHECK (type IN ('CLASS_INVITATION','ANNOUNCEMENT','ASSIGNMENT_PUBLISHED','ASSIGNMENT_REJUDGE',
                    'ASSIGNMENT_DEADLINE','SYSTEM','PUBLIC_COURSE_REVIEW'));

-- 7. Backend-only tables -----------------------------------------------------------------
DO $$
DECLARE tbl TEXT; browser_role TEXT;
BEGIN
 FOREACH tbl IN ARRAY ARRAY['tbl_public_course_revision','tbl_public_course_quiz',
                            'tbl_public_course_quiz_attempt','tbl_course_enrollment_event'] LOOP
  EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
  EXECUTE format('REVOKE ALL ON TABLE %I FROM PUBLIC', tbl);
  FOREACH browser_role IN ARRAY ARRAY['anon','authenticated'] LOOP
   IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = browser_role) THEN
    EXECUTE format('REVOKE ALL ON TABLE %I FROM %I', tbl, browser_role);
   END IF;
  END LOOP;
 END LOOP;
END $$;
