#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

HOOK_SOURCE="$ROOT_DIR/githooks/pre-push"
HOOK_TARGET="$(git rev-parse --git-path hooks)/pre-push"

if [[ ! -f "$HOOK_SOURCE" ]]; then
  echo "Missing hook source: $HOOK_SOURCE" >&2
  exit 1
fi

install -m 0755 "$HOOK_SOURCE" "$HOOK_TARGET"

echo "Installed pre-push hook at: $HOOK_TARGET"
echo "This hook runs: scripts/preflight.sh"
