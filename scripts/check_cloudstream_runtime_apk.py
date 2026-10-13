#!/usr/bin/env python3
"""Check that packaged CloudStream runtime DEX files do not duplicate host classes."""

from __future__ import annotations

import argparse
import io
import re
import struct
import sys
import zipfile
from pathlib import Path

try:
    from scripts.check_release_extension_abi import DexFile, read_u32
except ModuleNotFoundError:
    from check_release_extension_abi import DexFile, read_u32

APK_HOST_PREFIXES = (
    "eu/kanade/",
    "tachiyomi/",
    "uy/kohesive/",
)
HOST_RUNTIME_JAR = "assets/host-runtime.jar"
DEX_NAME = re.compile(r"^classes(?:\d+)?\.dex$")


def _dex_definitions(
    archive: zipfile.ZipFile,
    archive_label: str,
) -> dict[str, set[str]]:
    dex_names = sorted(name for name in archive.namelist() if DEX_NAME.fullmatch(name))
    if not dex_names:
        raise ValueError(f"{archive_label} contains no classes*.dex files")

    definitions: dict[str, set[str]] = {}
    for dex_name in dex_names:
        try:
            data = archive.read(dex_name)
            declared_count = read_u32(data, 96)
            dex = DexFile(data, dex_name)
            class_flags, _, _ = dex.member_flags()
        except (OSError, RuntimeError, struct.error, IndexError, UnicodeDecodeError, ValueError) as error:
            raise ValueError(f"{archive_label} has unreadable DEX {dex_name}: {error}") from error

        if declared_count != len(class_flags):
            raise ValueError(
                f"{archive_label} has inconsistent class definitions in {dex_name}: "
                f"header declares {declared_count}, reader found {len(class_flags)}",
            )
        definitions[dex_name] = set(class_flags)
    return definitions


def _read_shared_prefixes(path: Path) -> tuple[str, ...]:
    try:
        prefixes = tuple(
            line.strip()
            for line in path.read_text(encoding="utf-8").splitlines()
            if line.strip() and not line.lstrip().startswith("#")
        )
    except OSError as error:
        raise ValueError(f"cannot read shared-prefix file {path}: {error}") from error
    if not prefixes:
        raise ValueError(f"shared-prefix file {path} contains no prefixes")
    return prefixes


def _class_name(descriptor: str) -> str:
    if descriptor.startswith("L") and descriptor.endswith(";"):
        return descriptor[1:-1].replace("/", ".")
    return descriptor


def check_apk(apk_path: Path, shared_prefixes_path: Path) -> None:
    """Raise ValueError when the APK or embedded runtime jar defines host classes."""
    shared_prefixes = _read_shared_prefixes(shared_prefixes_path)
    try:
        with zipfile.ZipFile(apk_path) as apk:
            outer_definitions = _dex_definitions(apk, "outer APK")
            outer_leaks = sorted(
                (dex_name, class_name)
                for dex_name, classes in outer_definitions.items()
                for class_name in classes
                if any(class_name.startswith(f"L{prefix}") for prefix in APK_HOST_PREFIXES)
            )
            if outer_leaks:
                dex_name, descriptor = outer_leaks[0]
                raise ValueError(
                    f"outer APK {dex_name} defines forbidden host class {descriptor} "
                    f"({len(outer_leaks)} forbidden definition(s))",
                )

            try:
                nested_data = apk.read(HOST_RUNTIME_JAR)
            except KeyError as error:
                raise ValueError(f"outer APK is missing {HOST_RUNTIME_JAR}") from error
    except (OSError, RuntimeError, zipfile.BadZipFile) as error:
        raise ValueError(f"cannot read runtime APK {apk_path}: {error}") from error

    try:
        with zipfile.ZipFile(io.BytesIO(nested_data)) as host_runtime:
            nested_definitions = _dex_definitions(host_runtime, "nested host-runtime.jar")
    except (OSError, RuntimeError, zipfile.BadZipFile) as error:
        raise ValueError(f"cannot read nested {HOST_RUNTIME_JAR}: {error}") from error

    nested_leaks = sorted(
        (dex_name, _class_name(descriptor))
        for dex_name, classes in nested_definitions.items()
        for descriptor in classes
        if any(_class_name(descriptor).startswith(prefix) for prefix in shared_prefixes)
    )
    if nested_leaks:
        dex_name, class_name = nested_leaks[0]
        raise ValueError(
            f"nested host-runtime.jar {dex_name} defines host-shared class {class_name} "
            f"({len(nested_leaks)} shared definition(s))",
        )


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path, help="CloudStream runtime APK to inspect")
    parser.add_argument(
        "--shared-prefixes",
        required=True,
        type=Path,
        help="file with one host-shared package prefix per line",
    )
    args = parser.parse_args()

    try:
        check_apk(args.apk, args.shared_prefixes)
    except ValueError as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1

    print(f"Verified CloudStream runtime APK class ownership: {args.apk}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
