#!/usr/bin/env bash
# verify-plugin-compatibility.sh - Validate plugin manifest compatibility against host policy.
#
# Usage:
#   scripts/verify-plugin-compatibility.sh \
#     --manifest path/to/lightnovel-plugin-manifest.json \
#     --host-version 131 \
#     --host-channel stable \
#     --expected-api 1

set -euo pipefail

MANIFEST=""
HOST_VERSION=""
HOST_CHANNEL=""
EXPECTED_API=""
EXPECTED_PACKAGE="xyz.rayniyomi.plugin.lightnovel"

usage() {
  cat <<USAGE
Usage: $0 --manifest <file> --host-version <long> --host-channel <stable|beta> --expected-api <int>
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --manifest)
      MANIFEST="${2:-}"
      shift 2
      ;;
    --host-version)
      HOST_VERSION="${2:-}"
      shift 2
      ;;
    --host-channel)
      HOST_CHANNEL="${2:-}"
      shift 2
      ;;
    --expected-api)
      EXPECTED_API="${2:-}"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ -z "$MANIFEST" || -z "$HOST_VERSION" || -z "$HOST_CHANNEL" || -z "$EXPECTED_API" ]]; then
  usage >&2
  exit 2
fi

if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required" >&2
  exit 2
fi

if [[ ! -f "$MANIFEST" ]]; then
  echo "Manifest not found: $MANIFEST" >&2
  exit 2
fi

if [[ ! "$HOST_VERSION" =~ ^[0-9]+$ ]]; then
  echo "--host-version must be a non-negative integer" >&2
  exit 2
fi

if [[ ! "$EXPECTED_API" =~ ^[0-9]+$ ]]; then
  echo "--expected-api must be a non-negative integer" >&2
  exit 2
fi

if [[ "$HOST_CHANNEL" != "stable" && "$HOST_CHANNEL" != "beta" ]]; then
  echo "--host-channel must be stable or beta" >&2
  exit 2
fi

ERRORS=()

require_integer() {
  local key="$1"
  if ! jq -e ".${key} | type == \"number\" and floor == ." "$MANIFEST" >/dev/null; then
    ERRORS+=("$key must be an integer")
  fi
}

require_string() {
  local key="$1"
  if ! jq -e ".${key} | type == \"string\"" "$MANIFEST" >/dev/null; then
    ERRORS+=("$key must be a string")
  fi
}

require_string package_name
require_integer version_code
require_integer plugin_api_version
require_integer min_host_version
require_string apk_url
require_string apk_sha256
require_integer min_plugin_version_code
require_string release_channel

if ! jq -e '.target_host_version == null or (.target_host_version | type == "number" and floor == .)' "$MANIFEST" >/dev/null; then
  ERRORS+=("target_host_version must be null or integer")
fi

PLUGIN_API_VERSION=$(jq -r '.plugin_api_version' "$MANIFEST")
PACKAGE_NAME=$(jq -r '.package_name' "$MANIFEST")
MIN_HOST_VERSION=$(jq -r '.min_host_version' "$MANIFEST")
TARGET_HOST_VERSION=$(jq -r '.target_host_version // empty' "$MANIFEST")
PLUGIN_CHANNEL=$(jq -r '.release_channel' "$MANIFEST")
MIN_PLUGIN_VERSION=$(jq -r '.min_plugin_version_code' "$MANIFEST")
NORMALIZED_TARGET_HOST_VERSION="null"

if [[ "$PACKAGE_NAME" != "$EXPECTED_PACKAGE" ]]; then
  ERRORS+=("package_name mismatch: expected $EXPECTED_PACKAGE got $PACKAGE_NAME")
fi

if [[ "$PLUGIN_API_VERSION" != "$EXPECTED_API" ]]; then
  ERRORS+=("plugin_api_version mismatch: expected $EXPECTED_API got $PLUGIN_API_VERSION")
fi

if (( HOST_VERSION < MIN_HOST_VERSION )); then
  ERRORS+=("host version too old: host=$HOST_VERSION min_host_version=$MIN_HOST_VERSION")
fi

if [[ -n "$TARGET_HOST_VERSION" && "$TARGET_HOST_VERSION" != "null" ]]; then
  # target_host_version <= 0 is treated as unset to mirror runtime semantics.
  if (( TARGET_HOST_VERSION > 0 )); then
    NORMALIZED_TARGET_HOST_VERSION="$TARGET_HOST_VERSION"
    if (( HOST_VERSION > TARGET_HOST_VERSION )); then
      ERRORS+=("host version too new: host=$HOST_VERSION target_host_version=$TARGET_HOST_VERSION")
    fi
  fi
fi

if (( MIN_PLUGIN_VERSION < 0 )); then
  ERRORS+=("min_plugin_version_code must be >= 0")
fi

if [[ "$PLUGIN_CHANNEL" != "stable" && "$PLUGIN_CHANNEL" != "beta" ]]; then
  ERRORS+=("release_channel must be stable or beta (got $PLUGIN_CHANNEL)")
fi

if [[ "$HOST_CHANNEL" == "stable" && "$PLUGIN_CHANNEL" == "beta" ]]; then
  ERRORS+=("stable host channel cannot accept beta plugin release channel")
fi

if (( ${#ERRORS[@]} > 0 )); then
  ERRORS_JSON=$(printf '%s\n' "${ERRORS[@]}" | jq -R . | jq -s .)
  for error in "${ERRORS[@]}"; do
    echo "FAIL: $error" >&2
  done
  jq -n \
    --arg status "failed" \
    --arg manifest "$MANIFEST" \
    --argjson hostVersion "$HOST_VERSION" \
    --arg hostChannel "$HOST_CHANNEL" \
    --argjson expectedApi "$EXPECTED_API" \
    --argjson pluginApi "$PLUGIN_API_VERSION" \
    --arg pluginChannel "$PLUGIN_CHANNEL" \
    --argjson minHostVersion "$MIN_HOST_VERSION" \
    --argjson targetHostVersion "$NORMALIZED_TARGET_HOST_VERSION" \
    --argjson minPluginVersion "$MIN_PLUGIN_VERSION" \
    --argjson errors "$ERRORS_JSON" \
    '{
      status: $status,
      manifest: $manifest,
      hostVersionCode: $hostVersion,
      hostChannel: $hostChannel,
      expectedPluginApiVersion: $expectedApi,
      pluginApiVersion: $pluginApi,
      pluginChannel: $pluginChannel,
      minHostVersion: $minHostVersion,
      targetHostVersion: $targetHostVersion,
      minPluginVersionCode: $minPluginVersion,
      errors: $errors
    }'
  exit 1
fi

jq -n \
  --arg status "passed" \
  --arg manifest "$MANIFEST" \
  --argjson hostVersion "$HOST_VERSION" \
  --arg hostChannel "$HOST_CHANNEL" \
  --argjson expectedApi "$EXPECTED_API" \
  --argjson pluginApi "$PLUGIN_API_VERSION" \
  --arg pluginChannel "$PLUGIN_CHANNEL" \
  --argjson minHostVersion "$MIN_HOST_VERSION" \
  --argjson targetHostVersion "$NORMALIZED_TARGET_HOST_VERSION" \
  --argjson minPluginVersion "$MIN_PLUGIN_VERSION" \
  '{
    status: $status,
    manifest: $manifest,
    hostVersionCode: $hostVersion,
    hostChannel: $hostChannel,
    expectedPluginApiVersion: $expectedApi,
    pluginApiVersion: $pluginApi,
    pluginChannel: $pluginChannel,
    minHostVersion: $minHostVersion,
    targetHostVersion: $targetHostVersion,
    minPluginVersionCode: $minPluginVersion,
    errors: []
  }'
