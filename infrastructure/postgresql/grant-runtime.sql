-- Dedicated Bliss database only. Apply after migrations as the database administrator.
-- psql -X -v database=... -v runtime_role=... -v migration_role=... -f grant-runtime.sql
-- Roles/passwords are provisioned separately; this file contains no credentials.
\set ON_ERROR_STOP on

SELECT current_database() = :'database'
  AND current_user <> :'runtime_role'
  AND EXISTS (
    SELECT 1 FROM pg_roles r, pg_roles m
    WHERE r.rolname = :'runtime_role' AND m.rolname = :'migration_role'
      AND r.oid <> m.oid AND r.rolcanlogin
      AND NOT (r.rolsuper OR r.rolcreatedb OR r.rolcreaterole OR r.rolreplication OR r.rolbypassrls)
      AND NOT (m.rolsuper OR m.rolcreatedb OR m.rolcreaterole OR m.rolreplication OR m.rolbypassrls)
      AND NOT EXISTS (SELECT 1 FROM pg_auth_members a WHERE a.member = r.oid)
      AND NOT EXISTS (SELECT 1 FROM pg_class c WHERE c.relnamespace = 'public'::regnamespace AND c.relowner = r.oid)
      AND (SELECT nspowner FROM pg_namespace WHERE nspname = 'public') = m.oid
      AND (SELECT datdba FROM pg_database WHERE datname = current_database()) <> r.oid
  ) AND to_regclass('public.flyway_schema_history') IS NOT NULL AS policy_ok
\gset

\if :policy_ok
BEGIN;
-- Remove public CREATE/TEMP rights as well as direct runtime grants. This affects
-- all users of this dedicated database; never apply to a shared application DB.
REVOKE CREATE, TEMPORARY ON DATABASE :"database" FROM PUBLIC;
REVOKE ALL ON DATABASE :"database" FROM :"runtime_role";
GRANT CONNECT ON DATABASE :"database" TO :"runtime_role";
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
REVOKE ALL ON SCHEMA public FROM :"runtime_role";
GRANT USAGE ON SCHEMA public TO :"runtime_role";

REVOKE ALL ON ALL TABLES IN SCHEMA public FROM PUBLIC, :"runtime_role";
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO :"runtime_role";
-- Runtime may validate migration metadata but must never rewrite it.
REVOKE ALL ON TABLE public.flyway_schema_history FROM PUBLIC, :"runtime_role";
GRANT SELECT ON TABLE public.flyway_schema_history TO :"runtime_role";
-- Current schema uses UUIDs; allow sequence use if a later migration needs one,
-- without granting sequence UPDATE (setval) or object ownership.
REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM PUBLIC, :"runtime_role";
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO :"runtime_role";
COMMIT;
\else
-- PostgreSQL 17 psql does not accept an exit-code argument on \quit.
-- ON_ERROR_STOP turns this deliberate SQL error into exit status 3.
DO $bliss_policy$
BEGIN
  RAISE EXCEPTION 'Refusing runtime grants: database, role attributes/membership, ownership or migration prerequisite mismatch.';
END
$bliss_policy$;
\endif
