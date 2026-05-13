#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Usage: $0 <apk-path>" >&2
    exit 1
fi

APK_PATH=$1

if [[ ! -f "$APK_PATH" ]]; then
    echo "APK not found: $APK_PATH" >&2
    exit 1
fi

find_zipalign() {
    if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
        find "$ANDROID_SDK_ROOT/build-tools" -path '*/zipalign' -type f 2>/dev/null | sort -V | tail -n 1
        return
    fi

    if [[ -n "${ANDROID_HOME:-}" ]]; then
        find "$ANDROID_HOME/build-tools" -path '*/zipalign' -type f 2>/dev/null | sort -V | tail -n 1
        return
    fi
}

find_llvm_objdump() {
    if command -v llvm-objdump >/dev/null 2>&1; then
        command -v llvm-objdump
        return
    fi

    if command -v xcrun >/dev/null 2>&1 && xcrun --find llvm-objdump >/dev/null 2>&1; then
        xcrun --find llvm-objdump
        return
    fi

    if [[ -n "${ANDROID_NDK_ROOT:-}" ]]; then
        find "$ANDROID_NDK_ROOT/toolchains/llvm/prebuilt" -path '*/bin/llvm-objdump' -type f 2>/dev/null | head -n 1
        return
    fi

    if [[ -n "${ANDROID_SDK_ROOT:-}" ]]; then
        find "$ANDROID_SDK_ROOT/ndk" -path '*/toolchains/llvm/prebuilt/*/bin/llvm-objdump' -type f 2>/dev/null | head -n 1
        return
    fi
}

ZIPALIGN=$(find_zipalign)
OBJDUMP=$(find_llvm_objdump)

if [[ -z "${ZIPALIGN:-}" ]]; then
    echo "Unable to locate zipalign" >&2
    exit 1
fi

if [[ -z "${OBJDUMP:-}" ]]; then
    echo "Unable to locate llvm-objdump" >&2
    exit 1
fi

echo "Using zipalign: $ZIPALIGN"
echo "Using llvm-objdump: $OBJDUMP"

"$ZIPALIGN" -c -P 16 -v 4 "$APK_PATH"

TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

unzip -q "$APK_PATH" 'lib/arm64-v8a/*.so' -d "$TMP_DIR"

ARM64_LIST=$(mktemp)
trap 'rm -rf "$TMP_DIR" "$ARM64_LIST"' EXIT
find "$TMP_DIR/lib/arm64-v8a" -type f -name '*.so' | sort > "$ARM64_LIST"

if [[ ! -s "$ARM64_LIST" ]]; then
    echo "No arm64-v8a native libraries found in $APK_PATH" >&2
    exit 1
fi

printf 'arm64-v8a libraries:\n'
while IFS= read -r so_file; do
    printf ' - %s\n' "${so_file##*/}"
done < "$ARM64_LIST"

while IFS= read -r so_file; do
    if ! "$OBJDUMP" -p "$so_file" | awk '/LOAD/ { if ($0 !~ /align 2\*\*14/) exit 1 } END { exit 0 }'; then
        echo "16 KB alignment check failed for $(basename "$so_file")" >&2
        exit 1
    fi
done < "$ARM64_LIST"

echo "All packaged arm64-v8a native libraries are 16 KB aligned."
