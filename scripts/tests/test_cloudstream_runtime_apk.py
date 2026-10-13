from __future__ import annotations

import struct
import tempfile
import unittest
import zipfile
from pathlib import Path

from scripts.check_release_extension_abi import DexFile

ROOT = Path(__file__).resolve().parents[2]
SHARED_PREFIXES = ROOT / "cloudstream-runtime/shared-prefixes.txt"


def minimal_dex(classes: list[str]) -> bytes:
    """Build the class-definition subset that the repository DexFile reader consumes."""
    count = len(classes)
    string_ids_off = 112
    type_ids_off = string_ids_off + count * 4
    class_defs_off = type_ids_off + count * 4
    string_data_off = class_defs_off + count * 32

    data = bytearray(string_data_off)
    data[:8] = b"dex\n035\0"
    struct.pack_into("<II", data, 56, count, string_ids_off)
    struct.pack_into("<II", data, 64, count, type_ids_off)
    struct.pack_into("<II", data, 96, count, class_defs_off)

    for index, descriptor in enumerate(classes):
        encoded = descriptor.encode("ascii")
        string_offset = len(data)
        data.extend(bytes((len(encoded),)))
        data.extend(encoded)
        data.append(0)
        struct.pack_into("<I", data, string_ids_off + index * 4, string_offset)
        struct.pack_into("<I", data, type_ids_off + index * 4, index)
        struct.pack_into(
            "<8I",
            data,
            class_defs_off + index * 32,
            index,
            1,
            0xFFFFFFFF,
            0,
            0xFFFFFFFF,
            0,
            0,
            0,
        )

    actual, _, _ = DexFile(bytes(data), "fixture.dex").member_flags()
    if set(actual) != set(classes):
        raise AssertionError(f"DEX fixture definitions differ: {actual!r}")
    return bytes(data)


def write_apk(path: Path, outer_classes: list[str], nested_classes: list[str]) -> None:
    nested_bytes = minimal_dex(nested_classes)
    nested_buffer = tempfile.SpooledTemporaryFile()
    with zipfile.ZipFile(nested_buffer, "w") as nested:
        nested.writestr("classes.dex", nested_bytes)
    nested_buffer.seek(0)
    with zipfile.ZipFile(path, "w") as apk:
        apk.writestr("classes.dex", minimal_dex(outer_classes))
        apk.writestr("assets/host-runtime.jar", nested_buffer.read())
    nested_buffer.close()


def run_checker(test: unittest.TestCase, apk: Path) -> None:
    try:
        from scripts.check_cloudstream_runtime_apk import check_apk
    except ModuleNotFoundError as error:
        if error.name != "scripts.check_cloudstream_runtime_apk":
            raise
        test.fail("the runtime APK checker is not implemented")
    check_apk(apk, SHARED_PREFIXES)


class CloudStreamRuntimeApkTest(unittest.TestCase):
    def test_rejects_host_class_in_outer_apk_dex(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "outer-host-class.apk"
            write_apk(
                apk,
                ["Lxyz/rayniyomi/cloudstream/Entry;", "Leu/kanade/tachiyomi/HostType;"],
                ["Lxyz/rayniyomi/cloudstream/host/RuntimeFactory;"],
            )

            with self.assertRaisesRegex(ValueError, "outer APK.*Leu/kanade/tachiyomi/HostType"):
                run_checker(self, apk)

    def test_rejects_shared_class_in_nested_runtime_jar_dex(self) -> None:
        with tempfile.TemporaryDirectory() as directory:
            apk = Path(directory) / "nested-shared-class.apk"
            write_apk(
                apk,
                ["Lxyz/rayniyomi/cloudstream/Entry;"],
                [
                    "Lxyz/rayniyomi/cloudstream/host/RuntimeFactory;",
                    "Lkotlin/collections/SharedType;",
                ],
            )

            with self.assertRaisesRegex(ValueError, "nested host-runtime.jar.*kotlin.collections.SharedType"):
                run_checker(self, apk)


if __name__ == "__main__":
    unittest.main()
