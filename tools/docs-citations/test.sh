#!/usr/bin/env bash
# Cases for check.sh, run against the fixture tree in _fixtures/. A passing tree must exit 0; a
# failing tree must exit 1 and name each broken citation, and nothing else.
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
fixtures="$here/_fixtures"
failures=0

fail() {
  echo "FAIL: $1"
  failures=$((failures + 1))
}

if ! out="$(CITATIONS_ROOT="$fixtures" bash "$here/check.sh" "$fixtures/docs/pass" 2>&1)"; then
  fail "a tree with valid citations and a fenced invalid one exited non-zero: $out"
fi

set +e
out="$(CITATIONS_ROOT="$fixtures" bash "$here/check.sh" "$fixtures/docs/fail" 2>&1)"
status=$?
set -e

[ "$status" -eq 1 ] || fail "a tree with broken citations exited $status, not 1"
for expected in \
  "MISSING: $fixtures/docs/fail/fail.md: src/Absent.java" \
  "STALE: $fixtures/docs/fail/fail.md: src/Ledger.java lacks Ledger.reverse()" \
  "STALE: $fixtures/docs/fail/fail.md: src/Ledger.java lacks pos"; do
  grep -qxF -- "$expected" <<< "$out" || fail "missing line: $expected"
done
[ "$(grep -c '^\(MISSING\|STALE\): ' <<< "$out")" -eq 3 ] || fail "expected exactly three findings, got: $out"

if [ "$failures" -gt 0 ]; then
  echo "docs-citations test: $failures failure(s)" >&2
  exit 1
fi
echo "docs-citations test: all cases pass"
