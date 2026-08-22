-- Read-only migration-history export for Chaty production reconciliation.
-- Run against the target Supabase Postgres database. Do not mutate
-- supabase_migrations.schema_migrations manually.

select
  version,
  name,
  statements,
  rollback,
  created_by,
  idempotency_key
from supabase_migrations.schema_migrations
order by version;
