# Chaty Supabase Migration Reconciliation

## Status

**Reconciled on 2026-08-22.** The deployable repository chain now contains the same 45 migration versions and names recorded in the production Supabase migration ledger, from `20260819064501_chaty_core` through `20260822131153_e2ee_protocol_suite_invariant`.

The earlier repository-local consolidated versions were removed from the deployable migration directory. They remain recoverable from Git history only and must not be reintroduced.

## Production source of truth

Supabase records applied migrations in `supabase_migrations.schema_migrations`. During reconciliation the retained `statements[1]` SQL for every production migration was exported through a temporary repository-scoped GitHub OIDC path. The privileged temporary database export function was dropped immediately after synchronization and the temporary Edge Function was retired to a JWT-protected `410 Gone` response.

The repository now pins the normalized production SQL with:

- `supabase/migrations/*.sql` — the 45 deployable timestamped migrations.
- `supabase/MIGRATION_MANIFEST.sha256` — production-derived SHA-256 checksums for every migration file.
- `tools/check_migration_versions.sh` — fails unless exactly 45 unique 14-digit versions exist and every checksum matches.
- `tools/verify_supabase_schema.sql` — verifies critical schema, RLS, Storage, Realtime, call and E2EE invariants after replay.
- `.github/workflows/supabase-migration-reconciliation.yml` — performs checksum verification and a clean local Supabase replay on every hardening-branch push and pull request to `main`.

## Clean replay proof

The GitHub reconciliation workflow starts an empty local Supabase stack, runs `supabase db reset --local`, and then executes the invariant verification SQL. The first canonical-history replay completed successfully on 2026-08-22:

- canonical migration checksum validation: PASS
- local Supabase startup: PASS
- all 45 migrations replayed from zero: PASS
- required public schema/RLS checks: PASS
- private `chat-media` / `status-media` bucket checks: PASS
- critical Realtime publication checks: PASS
- call and E2EE schema/RPC invariants: PASS
- legacy password-taking login resolver remains unavailable to client roles: PASS

## Rules going forward

1. Never edit a migration already represented in `supabase/MIGRATION_MANIFEST.sha256`.
2. New migrations must use a unique 14-digit UTC-style timestamp and must be applied through the normal migration workflow rather than directly modifying production.
3. When a new migration is intentionally applied to production, update the checksum manifest in the same reviewed change.
4. `supabase db reset --local` and `tools/verify_supabase_schema.sql` must remain green before release.
5. Never use `migration repair` merely to hide repository/remote divergence. It changes migration bookkeeping, not schema state.
6. Do not seed production data through migration reconciliation.

Migration-history drift is no longer an accepted release exception.
