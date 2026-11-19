CREATE TABLE tbl_assignment_participation (
    assignment_id UUID NOT NULL REFERENCES tbl_assignment(id),
    student_id UUID NOT NULL REFERENCES tbl_account(id),
    extra_attempts INTEGER NOT NULL DEFAULT 0 CHECK(extra_attempts >= 0),
    reset_baseline BIGINT NOT NULL DEFAULT 0 CHECK(reset_baseline >= 0),
    unlocked_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY(assignment_id,student_id)
);
CREATE INDEX idx_assignment_participation_student ON tbl_assignment_participation(student_id);
CREATE TABLE tbl_assignment_evaluation (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES tbl_assignment_attempt(id),
    actor_id UUID REFERENCES tbl_account(id),
    kind VARCHAR(30) NOT NULL,
    generation BIGINT NOT NULL,
    UNIQUE(attempt_id,generation),
    auto_score NUMERIC(6,2), manual_score NUMERIC(6,2), overridden_score NUMERIC(6,2), final_score NUMERIC(6,2),
    complete BOOLEAN NOT NULL,
    reason TEXT,
    parts JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_assignment_evaluation_attempt ON tbl_assignment_evaluation(attempt_id);
CREATE INDEX idx_assignment_evaluation_actor ON tbl_assignment_evaluation(actor_id);
ALTER TABLE tbl_assignment_attempt ADD COLUMN current_evaluation_id UUID REFERENCES tbl_assignment_evaluation(id);
ALTER TABLE tbl_assignment_attempt ADD COLUMN published_evaluation_id UUID REFERENCES tbl_assignment_evaluation(id);
CREATE INDEX idx_assignment_attempt_current_evaluation ON tbl_assignment_attempt(current_evaluation_id);
CREATE INDEX idx_assignment_attempt_published_evaluation ON tbl_assignment_attempt(published_evaluation_id);
CREATE TRIGGER trg_assignment_evaluation_immutable BEFORE UPDATE OR DELETE ON tbl_assignment_evaluation
    FOR EACH ROW EXECUTE FUNCTION forbid_assignment_revision_mutation();
ALTER TABLE tbl_assignment_answer ADD COLUMN answer_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE tbl_assignment_answer ADD COLUMN client_mutation_id UUID;
ALTER TABLE tbl_assignment_participation ENABLE ROW LEVEL SECURITY;
ALTER TABLE tbl_assignment_evaluation ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON tbl_assignment_participation,tbl_assignment_evaluation FROM PUBLIC;
DO $$ DECLARE browser_role TEXT; BEGIN
    FOREACH browser_role IN ARRAY ARRAY['anon','authenticated'] LOOP
        IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=browser_role) THEN
            EXECUTE format('REVOKE ALL ON TABLE tbl_assignment_participation,tbl_assignment_evaluation FROM %I',browser_role);
        END IF;
    END LOOP;
END $$;

ALTER TABLE tbl_assignment_question ADD COLUMN code_content_snapshot JSONB;
CREATE TABLE tbl_assignment_code_job (
 id UUID PRIMARY KEY, attempt_id UUID NOT NULL REFERENCES tbl_assignment_attempt(id),
 question_id UUID NOT NULL REFERENCES tbl_assignment_question(id), job_id UUID NOT NULL,
 generation BIGINT NOT NULL, created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 UNIQUE(attempt_id,question_id,generation)
);
CREATE INDEX idx_assignment_code_job_question ON tbl_assignment_code_job(question_id);
CREATE INDEX idx_assignment_code_job_job ON tbl_assignment_code_job(job_id);
ALTER TABLE tbl_assignment_code_job ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON tbl_assignment_code_job FROM PUBLIC;

ALTER TABLE tbl_assignment_evaluation ADD CONSTRAINT uq_assignment_evaluation_identity UNIQUE(id,attempt_id);
ALTER TABLE tbl_assignment_attempt ADD CONSTRAINT fk_assignment_current_evaluation_scope
 FOREIGN KEY(current_evaluation_id,id) REFERENCES tbl_assignment_evaluation(id,attempt_id);
ALTER TABLE tbl_assignment_attempt ADD CONSTRAINT fk_assignment_published_evaluation_scope
 FOREIGN KEY(published_evaluation_id,id) REFERENCES tbl_assignment_evaluation(id,attempt_id);
DO $$ DECLARE browser_role TEXT; BEGIN
 FOREACH browser_role IN ARRAY ARRAY['anon','authenticated'] LOOP
  IF EXISTS(SELECT 1 FROM pg_roles WHERE rolname=browser_role) THEN
   EXECUTE format('REVOKE ALL ON TABLE tbl_assignment_code_job FROM %I',browser_role);
  END IF;
 END LOOP;
END $$;
ALTER TABLE tbl_assignment_question ADD COLUMN question_weight NUMERIC(12,6) NOT NULL DEFAULT 1 CHECK(question_weight>=0);
ALTER TABLE tbl_assignment_question ADD COLUMN code_case_weights JSONB;
ALTER TABLE tbl_assignment_answer ALTER COLUMN score TYPE NUMERIC(30,16);
ALTER TABLE tbl_assignment_question ADD COLUMN retired BOOLEAN NOT NULL DEFAULT false;
CREATE INDEX idx_assignment_active_question ON tbl_assignment_question(assignment_id,display_order) WHERE retired=false;

-- Durable projection checkpoint: applied code generations cannot block newer ready attempts.
ALTER TABLE tbl_assignment_code_job ADD COLUMN applied_at TIMESTAMPTZ;
CREATE INDEX idx_assignment_code_job_unapplied ON tbl_assignment_code_job(attempt_id,question_id,generation DESC) WHERE applied_at IS NULL;
