#!/usr/bin/env bash
set -euo pipefail

migration_dir="supabase/migrations"
manifest="supabase/MIGRATION_MANIFEST.sha256"
expected_count=48

if [[ ! -d "$migration_dir" ]]; then
  echo "FAIL: $migration_dir does not exist." >&2
  exit 1
fi
if [[ ! -f "$manifest" ]]; then
  echo "FAIL: canonical migration checksum manifest is missing: $manifest" >&2
  exit 1
fi

failed=0
count=0
declare -A seen=()

shopt -s nullglob
for path in "$migration_dir"/*.sql; do
  ((count += 1))
  file="$(basename "$path")"
  version="${file%%_*}"

  if [[ ! "$version" =~ ^[0-9]{14}$ ]]; then
    echo "FAIL: $file uses migration version '$version'; production ledger versions must be 14-digit timestamps." >&2
    failed=1
    continue
  fi

  if [[ -n "${seen[$version]:-}" ]]; then
    echo "FAIL: duplicate migration version $version in $file and ${seen[$version]}." >&2
    failed=1
  else
    seen[$version]="$file"
  fi
done

if (( count != expected_count )); then
  echo "FAIL: expected $expected_count canonical production migrations, found $count." >&2
  failed=1
fi

manifest_count="$(grep -Ec '^[0-9a-f]{64}  supabase/migrations/[0-9]{14}_[a-z0-9_]+\.sql$' "$manifest" || true)"
if [[ "$manifest_count" -ne "$expected_count" ]]; then
  echo "FAIL: checksum manifest must contain exactly $expected_count canonical entries; found $manifest_count." >&2
  failed=1
fi

if (( failed != 0 )); then
  echo "Migration version preflight failed. Reconcile against the production ledger before release." >&2
  exit 1
fi

sha256sum --check --strict "$manifest"

echo "Canonical migration preflight passed for $count migration files with production-derived SHA-256 checksums."
