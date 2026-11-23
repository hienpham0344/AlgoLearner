-- Internal JWTs are verified by Spring Boot. Browser roles have no direct database access.
-- The JDBC owner/migration role accesses these tables; no Supabase Auth policies are needed.
DO $$
DECLARE
    table_name TEXT;
    browser_role TEXT;
BEGIN
    FOREACH table_name IN ARRAY ARRAY['tbl_account', 'tbl_registration_otp', 'tbl_auth_session',
        'tbl_classroom', 'tbl_classroom_membership', 'tbl_classroom_join_request', 'tbl_classroom_invitation']
    LOOP
        EXECUTE format('ALTER TABLE %I ENABLE ROW LEVEL SECURITY', table_name);
        EXECUTE format('REVOKE ALL ON TABLE %I FROM PUBLIC', table_name);
        FOREACH browser_role IN ARRAY ARRAY['anon', 'authenticated'] LOOP
            IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = browser_role) THEN
                EXECUTE format('REVOKE ALL ON TABLE %I FROM %I', table_name, browser_role);
            END IF;
        END LOOP;
    END LOOP;
END $$;
