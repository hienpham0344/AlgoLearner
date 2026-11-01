CREATE TABLE tbl_account (
    id UUID PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(254) NOT NULL UNIQUE,
    role VARCHAR(20) NOT NULL CHECK (role IN ('STUDENT', 'TEACHER', 'ADMIN')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DEACTIVATED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE tbl_classroom (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES tbl_account(id),
    name VARCHAR(150) NOT NULL,
    description VARCHAR(2000),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    class_code VARCHAR(20) NOT NULL UNIQUE,
    code_expires_at TIMESTAMPTZ,
    code_locked BOOLEAN NOT NULL DEFAULT FALSE,
    joining_open BOOLEAN NOT NULL DEFAULT TRUE,
    join_mode VARCHAR(30) NOT NULL CHECK (join_mode IN ('AUTO_JOIN', 'REQUIRE_APPROVAL')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_classroom_owner_id ON tbl_classroom(owner_id);

CREATE TABLE tbl_classroom_membership (
    id UUID PRIMARY KEY,
    classroom_id UUID NOT NULL REFERENCES tbl_classroom(id),
    student_id UUID NOT NULL REFERENCES tbl_account(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'LEFT', 'REMOVED')),
    joined_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_classroom_student UNIQUE (classroom_id, student_id)
);
CREATE INDEX idx_classroom_membership_student_id ON tbl_classroom_membership(student_id);

CREATE TABLE tbl_classroom_join_request (
    id UUID PRIMARY KEY,
    classroom_id UUID NOT NULL REFERENCES tbl_classroom(id),
    student_id UUID NOT NULL REFERENCES tbl_account(id),
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING_APPROVAL', 'APPROVED', 'REJECTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_classroom_join_request_classroom_id ON tbl_classroom_join_request(classroom_id);
CREATE UNIQUE INDEX uq_pending_classroom_join_request ON tbl_classroom_join_request(classroom_id, student_id)
    WHERE status = 'PENDING_APPROVAL';

CREATE TABLE tbl_classroom_invitation (
    id UUID PRIMARY KEY,
    classroom_id UUID NOT NULL REFERENCES tbl_classroom(id),
    student_id UUID NOT NULL REFERENCES tbl_account(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'REVOKED', 'EXPIRED')),
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_classroom_invitation_classroom_id ON tbl_classroom_invitation(classroom_id);
CREATE INDEX idx_classroom_invitation_student_id ON tbl_classroom_invitation(student_id);
