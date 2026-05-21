#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

# Test: Verify baseline-prof.txt artifact is generated at expected path
# Acceptance criteria:
# 1. app/src/main/baseline-prof.txt exists
# 2. File is non-empty

ARTIFACT="app/src/main/baseline-prof.txt"

echo "Testing baseline profile artifact at $ARTIFACT..."

# Test 1: Verify artifact file exists
echo "Checking artifact exists at $ARTIFACT..."
if [ -f "$ARTIFACT" ]; then
  echo "✓ Artifact exists at $ARTIFACT"
else
  echo "✗ Artifact NOT found at $ARTIFACT"
  echo "Run ./gradlew :app:generateBaselineProfile to generate it"
  exit 1
fi

# Test 2: Verify artifact is non-empty
echo "Checking artifact is non-empty..."
if [ -s "$ARTIFACT" ]; then
  echo "✓ Artifact is non-empty ($(wc -l < "$ARTIFACT") lines)"
else
  echo "✗ Artifact exists but is empty: $ARTIFACT"
  exit 1
fi

# Test 3: Verify artifact has valid baseline profile format
# Valid lines start with L (class), PL, HSP, or SP prefixes
echo "Checking artifact has valid baseline profile format..."
VALID_COUNT=$(grep -cE '^[LHSP]' "$ARTIFACT") || true
if [ "$VALID_COUNT" -gt 0 ]; then
  echo "✓ Artifact contains $VALID_COUNT valid baseline profile entries"
else
  echo "✗ Artifact contains no valid baseline profile entries"
  echo "Expected lines starting with L, PL, HSP, or SP prefixes"
  exit 1
fi

echo "All tests passed!"
