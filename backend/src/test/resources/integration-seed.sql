-- IDs match MockData.id(username); only used by the local H2 HTTP integration run.
INSERT INTO tbl_account (id, username, email, role, status, created_at, updated_at) VALUES
('8d788385-4312-33d1-9e8b-43bb78f3aa41', 'teacher', 'teacher@example.test', 'TEACHER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ccffb0bb-993e-3b79-859b-31e1611ec353', 'teacher2', 'teacher2@example.test', 'TEACHER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('cd735028-2845-3d15-a55b-bd7a63fb0bc8', 'student', 'student@example.test', 'STUDENT', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('213ee683-360d-3824-9109-c2f92789dbc3', 'student2', 'student2@example.test', 'STUDENT', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('21232f29-7a57-35a7-8389-4a0e4a801fc3', 'admin', 'admin@example.test', 'ADMIN', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
