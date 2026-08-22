# Chaty Supabase Migration Reconciliation

## Purpose

The production Supabase project predates the current repository migration layout. The live ledger contains timestamped migrations from 2026-08-19 and 2026-08-20, while the repository contains several consolidated equivalents under later repository-local version names.

This is intentional historical drift, not permission to copy the live ledger into `supabase/migrations/` unchanged. Doing that would cause overlapping DDL and function/policy definitions to execute twice on a fresh project.

## Production source of truth

The production migration ledger is stored by Supabase in `supabase_migrations.schema_migrations` with these fields:

- `version`
- `name`
- `statements[]`
- `rollback[]`
- `created_by`
- `idempotency_key`

The `statements[]` column retains the exact SQL submitted for each live migration and is the authoritative historical record.

## Repository rule

`supabase/migrations/` is the deployable migration chain. Do not add an historical migration to that directory merely because its production version is absent from Git. First prove that its DDL is not already represented by a consolidated repository migration.

The current known overlaps include production migrations for task-status repair, rich-chat presence/privacy, profile visibility, call gating, call RPC hardening and the 2026-08-22 production-hardening migrations. Their logic is represented by repository migrations with repository-local numbering.

## Reconciliation procedure

1. Export the production ledger with `tools/export_supabase_migration_history.sql`.
2. Compare each production migration statement against the deployable repository chain by affected object: table, column, function signature, trigger, policy, index, bucket or storage policy.
3. Classify each production migration as `equivalent`, `superseded`, or `missing`.
4. Only migrations classified `missing` may be added to the deployable chain. Make those additions idempotent and verify ordering against every existing migration.
5. Do not rename an already-applied production migration and do not alter the production migration ledger manually.
6. Before declaring migration reproducibility complete, apply the repository chain to a clean disposable Supabase database/branch and run schema/RLS/security tests against it.

## Release gate

Migration reconciliation is complete only when a clean database created from the repository migration chain reaches the same required application schema, RLS policy set, RPC signatures, storage policies and E2EE/call invariants as production. A production project merely being healthy is not sufficient proof.
