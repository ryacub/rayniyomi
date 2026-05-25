#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.."

# Test: Verify CI workflow includes baseline profile generation step
# Acceptance criteria:
# 1. At least one workflow YAML includes generateBaselineProfile
# 2. The step fails the build if artifact is absent

WORKFLOWS_DIR=".github/workflows"

echo "Testing CI baseline profile workflow step..."

# Test 1: Verify at least one workflow includes generateBaselineProfile
echo "Checking for generateBaselineProfile in CI workflows..."
MATCHING_WORKFLOW=$(grep -rl "generateBaselineProfile" "$WORKFLOWS_DIR" 2>/dev/null | head -1 || true)

if [ -n "$MATCHING_WORKFLOW" ]; then
  echo "✓ Found generateBaselineProfile in: $MATCHING_WORKFLOW"
else
  echo "✗ No CI workflow contains generateBaselineProfile"
  echo "Expected a .github/workflows/*.yml file with a step running :app:generateBaselineProfile"
  exit 1
fi

# Test 2: Verify the workflow step checks artifact existence (not just runs the task)
echo "Checking workflow verifies artifact at app/src/main/baseline-prof.txt..."
if grep -q "baseline-prof.txt" "$MATCHING_WORKFLOW" 2>/dev/null; then
  echo "✓ Workflow references baseline-prof.txt artifact"
else
  echo "✗ Workflow does not verify baseline-prof.txt artifact"
  echo "CI step should fail if app/src/main/baseline-prof.txt is absent"
  exit 1
fi

echo "All tests passed!"
