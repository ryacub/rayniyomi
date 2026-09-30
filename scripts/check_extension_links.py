#!/usr/bin/env python3
"""List the host methods and fields that extension APKs reference and a host APK does not define.

An installed extension links against the host app at run time. If the host does
not define a member that the extension calls, the call throws NoSuchMethodError,
NoSuchFieldError, or NoClassDefFoundError. Neither build fails. R1078 found five
such members with this check.

The check follows superclasses and interfaces inside the host APK. It cannot see
platform classes, so a member that only a platform ancestor defines shows as
unresolved. Read those reports by hand.

The check sees member references only. It does not find a class that fails to
load, for example a missing superclass or an override of a method that R8 made
final (#817). check_release_extension_abi.py and a device test cover those.

Usage:
  ./gradlew assembleStableRelease
  ./scripts/check_extension_links.py \\
      app/build/outputs/apk/stable/release/app-stable-arm64-v8a-release-unsigned.apk \\
      extension1.apk [extension2.apk ...]

The exit status is 1 when a reference is unresolved, and 0 when all resolve.
"""

from __future__ import annotations

import argparse
import struct
import sys
from collections import Counter
from dataclasses import dataclass
from pathlib import Path

try:
    from scripts.check_release_extension_abi import DexFile, ancestors, dex_entries
except ModuleNotFoundError:
    from check_release_extension_abi import DexFile, ancestors, dex_entries

# An array type has no class definition. Its methods, such as clone(), come from Object.
PLATFORM_PREFIXES = (
    "[",
    "Landroid/",
    "Ljava/",
    "Ljavax/",
    "Ldalvik/",
    "Lorg/w3c/",
    "Lorg/xml/",
    "Lorg/json/",
    "Lorg/apache/http/",
)


@dataclass(frozen=True)
class HostSurface:
    """The members and the supertypes of each class that the host APK defines.

    A member is `name(parameters)return` for a method and `name:Type` for a field.
    """

    members: dict[str, set[str]]
    parents: dict[str, list[str]]


def split_signature(signature: str) -> tuple[str, str]:
    class_name, member = signature.split("->", 1)
    return class_name, member


def load_host(path: Path) -> HostSurface:
    members: dict[str, set[str]] = {}
    parents: dict[str, list[str]] = {}
    for name, data in dex_entries(path):
        dex = DexFile(data, name)
        dex_parents = dex.parents()
        parents.update(dex_parents)
        for class_name in dex_parents:
            members.setdefault(class_name, set())
        _, method_flags, field_flags = dex.member_flags()
        for signature in [*method_flags, *field_flags]:
            class_name, member = split_signature(signature)
            members.setdefault(class_name, set()).add(member)
    return HostSurface(members, parents)


def extension_references(path: Path) -> tuple[set[tuple[str, str]], set[str]]:
    """Return the members that the extension names, and the classes that it defines."""
    references: set[tuple[str, str]] = set()
    own_classes: set[str] = set()
    for name, data in dex_entries(path):
        dex = DexFile(data, name)
        own_classes.update(dex.parents())
        references.update(split_signature(signature) for signature in dex.referenced_members())
    return references, own_classes


def resolves(class_name: str, member: str, host: HostSurface) -> bool:
    return any(member in host.members.get(owner, ()) for owner in ancestors(class_name, host.parents))


def unresolved_references(
    references: set[tuple[str, str]],
    host: HostSurface,
    own_classes: set[str],
) -> list[tuple[str, str]]:
    return sorted(
        (class_name, member)
        for class_name, member in references
        if class_name not in own_classes
        and not class_name.startswith(PLATFORM_PREFIXES)
        and not resolves(class_name, member, host)
    )


def main() -> int:
    parser = argparse.ArgumentParser(
        description="List host members that extension APKs reference and the host APK does not define.",
    )
    parser.add_argument("host", type=Path, help="Host APK, normally a minified release build")
    parser.add_argument("extensions", type=Path, nargs="+", help="Extension APKs to check")
    args = parser.parse_args()

    try:
        host = load_host(args.host)
        print(f"host: {args.host.name}, {len(host.members)} classes")
        totals: Counter[str] = Counter()
        for extension in args.extensions:
            references, own_classes = extension_references(extension)
            unresolved = unresolved_references(references, host, own_classes)
            if not unresolved:
                continue
            print(f"\n== {extension.name}: {len(unresolved)} unresolved")
            for class_name, member in unresolved:
                absent = "" if class_name in host.members else "  [class absent]"
                print(f"  {class_name}->{member}{absent}")
                totals[f"{class_name}->{member}"] += 1
    except (OSError, ValueError, struct.error, IndexError) as error:
        print(f"ERROR: cannot inspect the APKs: {error}", file=sys.stderr)
        return 2

    print(f"\nchecked {len(args.extensions)} extensions, {sum(totals.values())} unresolved references")
    for signature, count in totals.most_common():
        print(f"  {count:5d}  {signature}")
    return 1 if totals else 0


if __name__ == "__main__":
    raise SystemExit(main())
