#!/usr/bin/env bash
# verify-plugin-release.sh - Verify a light novel plugin release artifact
#
# Usage: scripts/verify-plugin-release.sh <tag>
#   e.g.: scripts/verify-plugin-release.sh plugin-v0.1.0
#
# Requires: curl, sha256sum (or shasum), jq
# Optional: apksigner (from Android SDK build-tools)

set -euo pipefail

TAG="${1:-}"
REPO="ryacub/rayniyomi"
EXPECTED_PACKAGE="xyz.rayniyomi.plugin.lightnovel"
WORK_DIR=$(mktemp -d)
ERRORS=0

cleanup() {
    rm -rf "$WORK_DIR"
}
trap cleanup EXIT

# --- Argument validation ---

if [ -z "$TAG" ]; then
    echo "Usage: $0 <tag>"
    echo "  e.g.: $0 plugin-v0.1.0"
    exit 1
fi

RELEASE_URL="https://github.com/${REPO}/releases/download/${TAG}"

echo "=== Plugin Release Verification ==="
echo "Tag:  $TAG"
echo "Repo: $REPO"
echo ""

# --- Download manifest ---

echo "[1/5] Downloading manifest..."
MANIFEST_URL="${RELEASE_URL}/lightnovel-plugin-manifest.json"
if ! curl -fsSL -o "${WORK_DIR}/manifest.json" "$MANIFEST_URL"; then
    echo "FAIL: Could not download manifest from $MANIFEST_URL"
    exit 1
fi
echo "  OK: Manifest downloaded"

# --- Parse manifest ---

echo "[2/5] Parsing manifest..."
MANIFEST_PACKAGE=$(jq -r '.package_name' "${WORK_DIR}/manifest.json")
MANIFEST_VERSION_CODE=$(jq -r '.version_code' "${WORK_DIR}/manifest.json")
MANIFEST_APK_URL=$(jq -r '.apk_url' "${WORK_DIR}/manifest.json")
MANIFEST_SHA256=$(jq -r '.apk_sha256' "${WORK_DIR}/manifest.json")

echo "  Package:      $MANIFEST_PACKAGE"
echo "  Version Code: $MANIFEST_VERSION_CODE"
echo "  APK URL:      $MANIFEST_APK_URL"
echo "  SHA-256:      $MANIFEST_SHA256"

if [ "$MANIFEST_PACKAGE" != "$EXPECTED_PACKAGE" ]; then
    echo "FAIL: Package name mismatch: expected $EXPECTED_PACKAGE, got $MANIFEST_PACKAGE"
    ERRORS=$((ERRORS + 1))
fi

# --- Download APK ---

echo "[3/5] Downloading APK..."
APK_FILE="${WORK_DIR}/lightnovel-plugin-${TAG}.apk"
if ! curl -fsSL -o "$APK_FILE" "$MANIFEST_APK_URL"; then
    echo "FAIL: Could not download APK from $MANIFEST_APK_URL"
    exit 1
fi
echo "  OK: APK downloaded ($(wc -c < "$APK_FILE" | tr -d ' ') bytes)"

# --- Verify SHA-256 ---

echo "[4/5] Verifying SHA-256 checksum..."
if command -v sha256sum > /dev/null 2>&1; then
    ACTUAL_SHA256=$(sha256sum "$APK_FILE" | awk '{ print $1 }')
elif command -v shasum > /dev/null 2>&1; then
    ACTUAL_SHA256=$(shasum -a 256 "$APK_FILE" | awk '{ print $1 }')
else
    echo "WARN: Neither sha256sum nor shasum found; skipping checksum verification"
    ACTUAL_SHA256=""
fi

if [ -n "$ACTUAL_SHA256" ]; then
    if [ "$ACTUAL_SHA256" != "$MANIFEST_SHA256" ]; then
        echo "FAIL: SHA-256 mismatch"
        echo "  Expected: $MANIFEST_SHA256"
        echo "  Actual:   $ACTUAL_SHA256"
        ERRORS=$((ERRORS + 1))
    else
        echo "  OK: SHA-256 matches"
    fi
fi

# --- Verify APK signature (optional) ---

echo "[5/5] Verifying APK signature..."
if command -v apksigner > /dev/null 2>&1; then
    if apksigner verify --verbose "$APK_FILE" 2>&1; then
        echo "  OK: APK signature valid"
    else
        echo "FAIL: APK signature verification failed"
        ERRORS=$((ERRORS + 1))
    fi
elif [ -n "${ANDROID_HOME:-}" ]; then
    APKSIGNER="${ANDROID_HOME}/build-tools/35.0.1/apksigner"
    if [ -x "$APKSIGNER" ]; then
        if $APKSIGNER verify --verbose "$APK_FILE" 2>&1; then
            echo "  OK: APK signature valid"
        else
            echo "FAIL: APK signature verification failed"
            ERRORS=$((ERRORS + 1))
        fi
    else
        echo "SKIP: apksigner not found at $APKSIGNER"
    fi
else
    echo "SKIP: apksigner not available (set ANDROID_HOME or install Android SDK build-tools)"
fi

# --- Summary ---

echo ""
echo "=== Verification Summary ==="
if [ "$ERRORS" -gt 0 ]; then
    echo "FAILED: $ERRORS error(s) found"
    exit 1
else
    echo "PASSED: All checks passed"
    exit 0
fi
