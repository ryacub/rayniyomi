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

if ! grep -qE '^[[:space:]]*id\("androidx\.benchmark\.baselineprofile"\)' "$BUILD_GRADLE"; then
  echo "FAIL: id(\"androidx.benchmark.baselineprofile\") not declared in macrobenchmark/build.gradle.kts"
  fail=1
else
  echo "PASS: baseline profile plugin declared"
fi

if ! grep -qE 'targetProjectPath\s*=\s*":app"' "$BUILD_GRADLE"; then
  echo "FAIL: targetProjectPath = \":app\" not declared in macrobenchmark/build.gradle.kts"
  fail=1
else
  echo "PASS: targetProjectPath wired to :app"
fi

exit $fail
