#!/usr/bin/env bash
set -euo pipefail

migration_dir="supabase/migrations"
if [[ ! -d "$migration_dir" ]]; then
  echo "FAIL: $migration_dir does not exist." >&2
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

if (( count == 0 )); then
  echo "FAIL: no SQL migrations found in $migration_dir." >&2
  exit 1
fi

if (( failed != 0 )); then
  echo "Migration version preflight failed. Run the history reconciliation workflow before release." >&2
  exit 1
fi

echo "Migration version preflight passed for $count migration files."
