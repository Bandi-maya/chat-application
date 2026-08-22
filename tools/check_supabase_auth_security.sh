#!/usr/bin/env bash
set -euo pipefail

: "${SUPABASE_ACCESS_TOKEN:?SUPABASE_ACCESS_TOKEN is required}"
: "${SUPABASE_PROJECT_REF:?SUPABASE_PROJECT_REF is required}"

required_chars="abcdefghijklmnopqrstuvwxyz:ABCDEFGHIJKLMNOPQRSTUVWXYZ:0123456789:!@#$%^&*()_+-=[]{};'\\:\"|<>?,./\`~"
config_file="$(mktemp)"
trap 'rm -f "$config_file"' EXIT
chmod 600 "$config_file"

http_code="$({
  curl --silent --show-error \
    --output "$config_file" \
    --write-out '%{http_code}' \
    --header "Authorization: Bearer ${SUPABASE_ACCESS_TOKEN}" \
    "https://api.supabase.com/v1/projects/${SUPABASE_PROJECT_REF}/config/auth"
} || true)"

if [[ "$http_code" != "200" ]]; then
  echo "Supabase Auth security preflight could not read project configuration (HTTP ${http_code})." >&2
  exit 1
fi

hibp="$(jq -r '.password_hibp_enabled // false' "$config_file")"
min_length="$(jq -r '.password_min_length // 0' "$config_file")"
required="$(jq -r '.password_required_characters // ""' "$config_file")"

failed=0
if [[ "$hibp" != "true" ]]; then
  echo "FAIL: Supabase leaked-password protection is disabled." >&2
  failed=1
else
  echo "PASS: leaked-password protection is enabled."
fi

if ! [[ "$min_length" =~ ^[0-9]+$ ]] || (( min_length < 12 )); then
  echo "FAIL: Supabase minimum password length is ${min_length}; Chaty requires at least 12." >&2
  failed=1
else
  echo "PASS: hosted minimum password length is ${min_length}."
fi

if [[ "$required" != "$required_chars" ]]; then
  echo "FAIL: Supabase required-character policy is not the strongest lower/upper/digit/symbol option." >&2
  failed=1
else
  echo "PASS: hosted required-character policy matches Chaty's production policy."
fi

if (( failed != 0 )); then
  echo "Hosted Auth security configuration is not release-ready." >&2
  exit 1
fi

echo "Supabase Auth security preflight passed."
