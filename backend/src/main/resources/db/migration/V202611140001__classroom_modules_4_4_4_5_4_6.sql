-- Migration: V202611140001__classroom_modules_4_4_4_5_4_6.sql
-- Modules 4.4 (Announcement), 4.5 (Lesson), 4.6 (Assignment), and Notification upgrade

-- 1. Upgrade tbl_notification for multi-source classroom notifications
ALTER TABLE tbl_notification ALTER COLUMN classroom_invitation_id DROP NOT NULL;
ALTER TABLE tbl_notification DROP CONSTRAINT IF EXISTS tbl_notification_type_check;
ALTER TABLE tbl_notification ADD CONSTRAINT tbl_notification_type_check 
    CHECK (type IN ('CLASS_INVITATION', 'ANNOUNCEMENT', 'ASSIGNMENT_PUBLISHED', 'ASSIGNMENT_REJUDGE', 'ASSIGNMENT_DEADLINE', 'SYSTEM'));

ALTER TABLE tbl_notification ADD COLUMN IF NOT EXISTS title VARCHAR(255);
ALTER TABLE tbl_notification ADD COLUMN IF NOT EXISTS message TEXT;
ALTER TABLE tbl_notification ADD COLUMN IF NOT EXISTS link_url VARCHAR(512);

-- 2. Module 4.4: Classroom Announcements
CREATE TABLE tbl_announcement (
    id UUID PRIMARY KEY,
    classroom_id UUID NOT NULL REFERENCES tbl_classroom(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES tbl_account(id),
    title VARCHAR(255) NOT NULL,
    content TEXT NOT NULL,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    pinned_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_announcement_classroom_id ON tbl_announcement(classroom_id);
CREATE INDEX idx_announcement_sort_order ON tbl_announcement(classroom_id, is_pinned DESC, pinned_at DESC, created_at DESC);

-- Link tbl_notification to announcement if applicable
ALTER TABLE tbl_notification ADD COLUMN IF NOT EXISTS announcement_id UUID REFERENCES tbl_announcement(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_notification_announcement_id ON tbl_notification(announcement_id);

-- 3. Module 4.5: Classroom Lessons
CREATE TABLE tbl_lesson (
    id UUID PRIMARY KEY,
    classroom_id UUID NOT NULL REFERENCES tbl_classroom(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    content TEXT,
    code_example TEXT,
    external_links JSONB DEFAULT '[]'::jsonb,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    completion_mode VARCHAR(30) NOT NULL DEFAULT 'MANUAL' CHECK (completion_mode IN ('MANUAL', 'AUTO_ITEMS', 'NONE')),
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_lesson_classroom_status ON tbl_lesson(classroom_id, status, display_order ASC);

CREATE TABLE tbl_lesson_material (
    id UUID PRIMARY KEY,
    lesson_id UUID NOT NULL REFERENCES tbl_lesson(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    file_url VARCHAR(512) NOT NULL,
    file_type VARCHAR(50) NOT NULL CHECK (file_type IN ('PDF', 'DOCX', 'PPTX', 'ZIP', 'IMAGE')),
    file_size BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_lesson_material_lesson_id ON tbl_lesson_material(lesson_id);

CREATE TABLE tbl_lesson_linked_item (
    id UUID PRIMARY KEY,
    lesson_id UUID NOT NULL REFERENCES tbl_lesson(id) ON DELETE CASCADE,
    item_type VARCHAR(30) NOT NULL CHECK (item_type IN ('ASSIGNMENT', 'QUIZ', 'PRACTICE')),
    item_id UUID NOT NULL,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_lesson_linked_item_lesson_id ON tbl_lesson_linked_item(lesson_id);
-- BR-LES-002: Mỗi Quiz chỉ liên kết tối đa với một Lesson
CREATE UNIQUE INDEX uq_lesson_linked_quiz ON tbl_lesson_linked_item(item_id) WHERE item_type = 'QUIZ';

CREATE TABLE tbl_lesson_completion (
    id UUID PRIMARY KEY,
    lesson_id UUID NOT NULL REFERENCES tbl_lesson(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES tbl_account(id) ON DELETE CASCADE,
    is_completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_lesson_completion UNIQUE (lesson_id, student_id)
);

CREATE INDEX idx_lesson_completion_student ON tbl_lesson_completion(student_id);

-- 4. Module 4.6: Classroom Assignments
CREATE TABLE tbl_assignment (
    id UUID PRIMARY KEY,
    classroom_id UUID NOT NULL REFERENCES tbl_classroom(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    type VARCHAR(30) NOT NULL DEFAULT 'GRADED' CHECK (type IN ('GRADED', 'UNGRADED')),
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'SCHEDULED', 'PUBLISHED', 'CLOSED')),
    start_time TIMESTAMPTZ,
    deadline TIMESTAMPTZ,
    allow_late_submission BOOLEAN NOT NULL DEFAULT FALSE,
    late_penalty_percent NUMERIC(5,2) DEFAULT 0,
    duration_minutes INT,
    time_expired_action VARCHAR(30) NOT NULL DEFAULT 'AUTO_SUBMIT' CHECK (time_expired_action IN ('AUTO_SUBMIT', 'LOCK_WITHOUT_SUBMIT')),
    max_attempts INT NOT NULL DEFAULT 1,
    scoring_policy VARCHAR(30) NOT NULL DEFAULT 'HIGHEST' CHECK (scoring_policy IN ('HIGHEST', 'LATEST')),
    shuffle_questions BOOLEAN NOT NULL DEFAULT FALSE,
    shuffle_answers BOOLEAN NOT NULL DEFAULT FALSE,
    grade_publish_mode VARCHAR(30) NOT NULL DEFAULT 'MANUAL' CHECK (grade_publish_mode IN ('IMMEDIATE', 'AFTER_DEADLINE', 'MANUAL')),
    is_grade_published BOOLEAN NOT NULL DEFAULT FALSE,
    view_answers_mode VARCHAR(30) NOT NULL DEFAULT 'NEVER' CHECK (view_answers_mode IN ('ALWAYS', 'AFTER_DEADLINE', 'AFTER_SUBMIT', 'NEVER')),
    code_feedback_level VARCHAR(30) NOT NULL DEFAULT 'DETAILED' CHECK (code_feedback_level IN ('SUMMARY', 'DETAILED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_assignment_classroom_status ON tbl_assignment(classroom_id, status);

-- Link tbl_notification to assignment if applicable
ALTER TABLE tbl_notification ADD COLUMN IF NOT EXISTS assignment_id UUID REFERENCES tbl_assignment(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_notification_assignment_id ON tbl_notification(assignment_id);

CREATE TABLE tbl_assignment_question (
    id UUID PRIMARY KEY,
    assignment_id UUID NOT NULL REFERENCES tbl_assignment(id) ON DELETE CASCADE,
    question_type VARCHAR(30) NOT NULL CHECK (question_type IN ('CODE', 'SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE', 'ESSAY')),
    title VARCHAR(255) NOT NULL,
    prompt TEXT NOT NULL,
    points NUMERIC(6,2) NOT NULL DEFAULT 10.0,
    display_order INT NOT NULL DEFAULT 0,
    grading_policy VARCHAR(30) DEFAULT 'ALL_OR_NOTHING' CHECK (grading_policy IN ('ALL_OR_NOTHING', 'PARTIAL_CREDIT')),
    essay_format VARCHAR(30) CHECK (essay_format IN ('TEXT_ONLY', 'FILE_ONLY', 'TEXT_AND_FILE')),
    explanation TEXT,
    code_template TEXT,
    code_language VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_assignment_question_assignment ON tbl_assignment_question(assignment_id, display_order ASC);

CREATE TABLE tbl_assignment_question_option (
    id UUID PRIMARY KEY,
    question_id UUID NOT NULL REFERENCES tbl_assignment_question(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    is_correct BOOLEAN NOT NULL DEFAULT FALSE,
    display_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_assignment_option_question ON tbl_assignment_question_option(question_id, display_order ASC);

CREATE TABLE tbl_assignment_attempt (
    id UUID PRIMARY KEY,
    assignment_id UUID NOT NULL REFERENCES tbl_assignment(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES tbl_account(id) ON DELETE CASCADE,
    attempt_number INT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'IN_PROGRESS' CHECK (status IN ('IN_PROGRESS', 'SUBMITTED', 'GRADED', 'TIMED_OUT')),
    started_at TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ,
    is_late BOOLEAN NOT NULL DEFAULT FALSE,
    auto_score NUMERIC(6,2),
    manual_score NUMERIC(6,2),
    overridden_score NUMERIC(6,2),
    final_score NUMERIC(6,2),
    override_reason TEXT,
    teacher_feedback TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_assignment_attempt UNIQUE (assignment_id, student_id, attempt_number)
);

CREATE INDEX idx_assignment_attempt_student ON tbl_assignment_attempt(student_id);
CREATE INDEX idx_assignment_attempt_assignment ON tbl_assignment_attempt(assignment_id);

CREATE TABLE tbl_assignment_answer (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES tbl_assignment_attempt(id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES tbl_assignment_question(id) ON DELETE CASCADE,
    selected_option_ids JSONB,
    text_answer TEXT,
    file_url VARCHAR(512),
    code_submission TEXT,
    is_draft BOOLEAN NOT NULL DEFAULT TRUE,
    score NUMERIC(6,2),
    feedback TEXT,
    last_saved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_attempt_question_answer UNIQUE (attempt_id, question_id)
);

CREATE INDEX idx_assignment_answer_attempt ON tbl_assignment_answer(attempt_id);

CREATE TABLE tbl_assignment_rejudge_log (
    id UUID PRIMARY KEY,
    assignment_id UUID NOT NULL REFERENCES tbl_assignment(id) ON DELETE CASCADE,
    attempt_id UUID NOT NULL REFERENCES tbl_assignment_attempt(id) ON DELETE CASCADE,
    performed_by UUID NOT NULL REFERENCES tbl_account(id),
    old_score NUMERIC(6,2),
    new_score NUMERIC(6,2),
    reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_assignment_rejudge_assignment ON tbl_assignment_rejudge_log(assignment_id);

-- RLS & Security Revocation
DO $$
DECLARE
    tbl text;
    browser_role text;
    tables text[] := ARRAY[
        'tbl_announcement',
        'tbl_lesson',
        'tbl_lesson_material',
        'tbl_lesson_linked_item',
        'tbl_lesson_completion',
        'tbl_assignment',
        'tbl_assignment_question',
        'tbl_assignment_question_option',
        'tbl_assignment_attempt',
        'tbl_assignment_answer',
        'tbl_assignment_rejudge_log'
    ];
BEGIN
    FOREACH tbl IN ARRAY tables LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', tbl);
        EXECUTE format('REVOKE ALL ON TABLE %I FROM PUBLIC', tbl);
        FOREACH browser_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
            IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = browser_role) THEN
                EXECUTE format('REVOKE ALL ON TABLE %I FROM %I', tbl, browser_role);
            END IF;
        END LOOP;
    END LOOP;
END $$;
