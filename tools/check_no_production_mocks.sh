#!/usr/bin/env bash
set -euo pipefail

# Fail closed: a missing tool must never produce a vacuous PASS.
command -v grep >/dev/null 2>&1 || {
  echo 'FAIL: grep is unavailable; mock guard cannot run meaningfully.' >&2
  exit 1
}

fail=0

if grep -RIn --include='*.dart' -E 'MockDataStore|mock_data_store\.dart' lib/; then
  echo 'ERROR: production Dart graph still references MockDataStore.' >&2
  fail=1
fi

if grep -RIn --include='*.dart' -E 'usr_guest|google\.user@gmail\.com|apple\.id@icloud\.com|facebook\.user@fb\.com' lib/; then
  echo 'ERROR: seeded/demo identity material remains in production Dart.' >&2
  fail=1
fi

if grep -RIn --include='*.dart' -E 'startMockCallForQA|qa_local_preview|Local media QA|mock call|demo account|DemoAccountChooser' lib/; then
  echo 'ERROR: QA/demo/mock execution paths remain in production Dart.' >&2
  fail=1
fi

if grep -RIn --include='*.dart' -E 'TODO[[:space:]]*:[[:space:]]*(production|security|backend|realtime)|placeholder[[:space:]]+(backend|data|success)' lib/; then
  echo 'ERROR: production-critical TODO/placeholder marker remains in lib/.' >&2
  fail=1
fi

if [[ $fail -ne 0 ]]; then
  exit 1
fi

echo 'Production mock/placeholder guard passed.'
