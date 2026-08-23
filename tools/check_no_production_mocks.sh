#!/usr/bin/env bash
set -euo pipefail

fail=0

if grep -RIn --include='*.dart' -E 'MockDataStore|mock_data_store\.dart' lib/; then
  echo 'ERROR: production Dart graph still references MockDataStore.' >&2
  fail=1
fi

if grep -RIn --include='*.dart' -E 'usr_guest|google\.user@gmail\.com|apple\.id@icloud\.com|facebook\.user@fb\.com' lib/; then
  echo 'ERROR: seeded/demo identity material remains in production Dart.' >&2
  fail=1
fi

# Debug-only QA paths are permitted only when their entry is statically gated.
if grep -RIn --include='*.dart' 'startMockCallForQA' lib/ | grep -v 'call_signaling_service.dart' >/tmp/chaty_mock_call_refs.txt; then
  if [[ -s /tmp/chaty_mock_call_refs.txt ]]; then
    while IFS= read -r ref; do
      file=${ref%%:*}
      if ! grep -q 'kDebugMode' "$file"; then
        echo "ERROR: mock call QA reference is not debug-gated: $ref" >&2
        fail=1
      fi
    done </tmp/chaty_mock_call_refs.txt
  fi
fi

if [[ $fail -ne 0 ]]; then
  exit 1
fi

echo 'Production mock/placeholder guard passed.'
