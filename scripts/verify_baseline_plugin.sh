#!/usr/bin/env bash
# Verify that macrobenchmark/build.gradle.kts declares the baseline profile plugin
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_GRADLE="$SCRIPT_DIR/../macrobenchmark/build.gradle.kts"

if [[ ! -f "$BUILD_GRADLE" ]]; then
  echo "FAIL: macrobenchmark/build.gradle.kts not found at $BUILD_GRADLE"
  exit 1
fi

fail=0

if ! grep -q "androidx.benchmark.baselineprofile" "$BUILD_GRADLE"; then
  echo "FAIL: 'androidx.benchmark.baselineprofile' plugin not declared in macrobenchmark/build.gradle.kts"
  fail=1
else
  echo "PASS: baseline profile plugin declared"
fi

if ! grep -qE "baselineProfile|baseline-prof|generateBaselineProfile" "$BUILD_GRADLE"; then
  echo "FAIL: no baseline profile output configuration found in macrobenchmark/build.gradle.kts"
  fail=1
else
  echo "PASS: baseline profile configuration present"
fi

exit $fail
