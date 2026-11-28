-- Run as the migration owner in the application schema after migrations.
-- A DBA creates algolearner_runtime as a dedicated LOGIN, NOSUPERUSER,
-- NOCREATEDB, NOCREATEROLE, NOREPLICATION, NOBYPASSRLS role and sets its secret.
-- Never grant it membership in the migration owner or browser-facing roles.
BEGIN;
DO $$
DECLARE
    runtime_name text := 'algolearner_runtime';
    schema_name text := current_schema();
    item record;
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname=runtime_name
            AND NOT rolsuper AND NOT rolbypassrls AND NOT rolcreaterole
            AND NOT rolcreatedb AND NOT rolreplication) THEN
        RAISE EXCEPTION 'Create a dedicated unprivileged runtime role first';
    END IF;
    IF pg_has_role(runtime_name, current_user, 'MEMBER') THEN
        RAISE EXCEPTION 'Runtime must not inherit the migration owner';
    END IF;
    EXECUTE format('REVOKE CREATE ON SCHEMA %I FROM PUBLIC', schema_name);
    EXECUTE format('REVOKE CREATE ON SCHEMA %I FROM %I', schema_name, runtime_name);
    EXECUTE format('GRANT USAGE ON SCHEMA %I TO %I', schema_name, runtime_name);
    FOR item IN SELECT c.relname,c.relkind,c.relowner FROM pg_class c
            JOIN pg_namespace n ON n.oid=c.relnamespace
            WHERE n.nspname=schema_name AND c.relkind IN ('r','p','v')
            AND (starts_with(c.relname,'tbl_') OR starts_with(c.relname,'vw_')) LOOP
        IF pg_get_userbyid(item.relowner) <> current_user THEN
            RAISE EXCEPTION 'All application objects must belong to the migration owner';
        END IF;
        EXECUTE format('REVOKE ALL ON %I.%I FROM %I',schema_name,item.relname,runtime_name);
        IF item.relkind='v' THEN
            EXECUTE format('GRANT SELECT ON %I.%I TO %I',schema_name,item.relname,runtime_name);
        ELSE
            EXECUTE format('GRANT SELECT, INSERT, UPDATE, DELETE ON %I.%I TO %I',schema_name,item.relname,runtime_name);
            EXECUTE format('ALTER TABLE %I.%I ENABLE ROW LEVEL SECURITY',schema_name,item.relname);
            EXECUTE format('DROP POLICY IF EXISTS backend_runtime_access ON %I.%I',schema_name,item.relname);
            EXECUTE format('CREATE POLICY backend_runtime_access ON %I.%I TO %I, %I USING (true) WITH CHECK (true)',
                schema_name,item.relname,runtime_name,current_user);
            EXECUTE format('ALTER TABLE %I.%I FORCE ROW LEVEL SECURITY',schema_name,item.relname);
        END IF;
    END LOOP;
    -- Audit is append-only to the API credential.
    IF to_regclass('tbl_audit_log') IS NOT NULL THEN
        EXECUTE format('REVOKE UPDATE, DELETE ON tbl_audit_log FROM %I',runtime_name);
    END IF;
    FOR item IN SELECT c.relname FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace
            WHERE n.nspname=schema_name AND c.relkind='S' AND starts_with(c.relname,'tbl_') LOOP
        EXECUTE format('GRANT USAGE, SELECT ON SEQUENCE %I.%I TO %I',schema_name,item.relname,runtime_name);
    END LOOP;
END $$;
COMMIT;
