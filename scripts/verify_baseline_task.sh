#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

# Test: Verify :app:generateBaselineProfile task exists and can be invoked
# Acceptance criteria:
# 1. `./gradlew :app:generateBaselineProfile --dry-run` exits 0
# 2. Task appears in `./gradlew :app:tasks --group=verification`

echo "Testing :app:generateBaselineProfile task existence and invocation..."

# Test 1: Verify task exists in task list (--group=verification)
echo "Checking task list for :app:generateBaselineProfile..."
if ./gradlew :app:tasks --group=verification 2>&1 | grep -q ":app:generateBaselineProfile"; then
  echo "✓ Task found in :app:tasks --group=verification"
else
  echo "✗ Task NOT found in :app:tasks --group=verification"
  echo "Available verification tasks:"
  ./gradlew :app:tasks --group=verification || true
  exit 1
fi

# Test 2: Verify task can be invoked with --dry-run (exits 0)
echo "Testing :app:generateBaselineProfile --dry-run..."
if ./gradlew :app:generateBaselineProfile --dry-run > /dev/null 2>&1; then
  echo "✓ :app:generateBaselineProfile --dry-run exited successfully"
else
  echo "✗ :app:generateBaselineProfile --dry-run failed"
  ./gradlew :app:generateBaselineProfile --dry-run || true
  exit 1
fi

echo "All tests passed!"
