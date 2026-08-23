#!/usr/bin/env bash
set -euo pipefail

: "${SUPABASE_ACCESS_TOKEN:?SUPABASE_ACCESS_TOKEN is required}"
: "${SUPABASE_PROJECT_REF:?SUPABASE_PROJECT_REF is required}"

required_chars='abcdefghijklmnopqrstuvwxyz:ABCDEFGHIJKLMNOPQRSTUVWXYZ:0123456789:!@#$%^&*()_+-=[]{};'"'"'\:"|<>?,./`~'
payload_file="$(mktemp)"
response_file="$(mktemp)"
trap 'rm -f "$payload_file" "$response_file"' EXIT
chmod 600 "$payload_file" "$response_file"

jq -n \
  --arg chars "$required_chars" \
  '{
    password_hibp_enabled: true,
    password_min_length: 12,
    password_required_characters: $chars
  }' > "$payload_file"

http_code="$({
  curl --silent --show-error \
    --request PATCH \
    --output "$response_file" \
    --write-out '%{http_code}' \
    --header "Authorization: Bearer ${SUPABASE_ACCESS_TOKEN}" \
    --header 'Content-Type: application/json' \
    --data-binary "@$payload_file" \
    "https://api.supabase.com/v1/projects/${SUPABASE_PROJECT_REF}/config/auth"
} || true)"

if [[ "$http_code" != "200" ]]; then
  echo "Failed to harden Supabase Auth configuration (HTTP ${http_code})." >&2
  jq -c '{message, error, code}' "$response_file" 2>/dev/null >&2 || true
  exit 1
fi

bash tools/check_supabase_auth_security.sh
