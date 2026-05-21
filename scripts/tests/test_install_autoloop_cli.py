from __future__ import annotations

import os
import shutil
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
INSTALLER = REPO_ROOT / "scripts" / "install-autoloop-cli.sh"


class InstallAutoloopCliTest(unittest.TestCase):
    def _make_fake_home(self, tmpdir: Path, *, help_fails: bool = False) -> Path:
        home = tmpdir / "autoloop-home"
        scripts = home / "scripts"
        scripts.mkdir(parents=True)
        runner = scripts / "autoloop_runner.py"
        help_block = (
            "if '--help' in sys.argv:\n"
            "    print('fake help')\n"
            "    raise SystemExit(0)\n"
        )
        if help_fails:
            help_block = (
                "if '--help' in sys.argv:\n"
                "    print('broken help', file=sys.stderr)\n"
                "    raise SystemExit(12)\n"
            )
        runner.write_text(
            "#!/usr/bin/env python3\n"
            "import sys\n"
            f"{help_block}"
            "print('|'.join(sys.argv[1:]))\n",
            encoding="utf-8",
        )
        runner.chmod(0o755)
        return home

    def _run_installer(
        self,
        tmpdir: Path,
        *,
        autoloop_home: Path | None = None,
        install_dir: Path | None = None,
        path_prefix: list[Path] | None = None,
        set_install_dir: bool = True,
        append_system_path: bool = True,
    ) -> subprocess.CompletedProcess[str]:
        if install_dir is None:
            install_dir = tmpdir / "bin"
            install_dir.mkdir()
        env = os.environ.copy()
        if set_install_dir:
            env["AUTOLOOP_INSTALL_DIR"] = str(install_dir)
        else:
            env.pop("AUTOLOOP_INSTALL_DIR", None)
        prefixes = path_prefix if path_prefix is not None else [install_dir]
        path_parts = [*(str(path) for path in prefixes)]
        if append_system_path:
            path_parts.append(env["PATH"])
        env["PATH"] = os.pathsep.join(path_parts)
        if autoloop_home is not None:
            env["AUTOLOOP_HOME"] = str(autoloop_home)
        return subprocess.run(
            [str(INSTALLER)],
            cwd=REPO_ROOT,
            env=env,
            text=True,
            capture_output=True,
            check=False,
        )

    def test_installs_executable_wrapper_and_forwards_arguments(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmpdir = Path(tmp)
            autoloop_home = self._make_fake_home(tmpdir)

            result = self._run_installer(tmpdir, autoloop_home=autoloop_home)

            self.assertEqual(result.returncode, 0, result.stderr)
            wrapper = tmpdir / "bin" / "autoloop"
            self.assertTrue(wrapper.exists())
            self.assertTrue(wrapper.stat().st_mode & stat.S_IXUSR)

            env = os.environ.copy()
            env["PATH"] = f"{wrapper.parent}{os.pathsep}{env['PATH']}"
            env["AUTOLOOP_HOME"] = str(autoloop_home)
            forwarded = subprocess.run(
                ["autoloop", "preflight", "650", "--phase", "plan"],
                env=env,
                text=True,
                capture_output=True,
                check=False,
            )
            self.assertEqual(forwarded.returncode, 0, forwarded.stderr)
            self.assertIn("preflight|650|--phase|plan", forwarded.stdout)

    def test_fails_when_runner_is_missing(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmpdir = Path(tmp)
            missing_home = tmpdir / "missing-autoloop-home"

            result = self._run_installer(tmpdir, autoloop_home=missing_home)

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("runner not found", result.stderr)

    def test_fails_when_runner_help_fails(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmpdir = Path(tmp)
            autoloop_home = self._make_fake_home(tmpdir, help_fails=True)

            result = self._run_installer(tmpdir, autoloop_home=autoloop_home)

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("runner help failed", result.stderr)

    def test_fails_when_explicit_install_dir_is_not_on_path(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmpdir = Path(tmp)
            autoloop_home = self._make_fake_home(tmpdir)
            install_dir = tmpdir / "not-on-path"
            install_dir.mkdir()
            path_dir = tmpdir / "path-bin"
            path_dir.mkdir()

            result = self._run_installer(
                tmpdir,
                autoloop_home=autoloop_home,
                install_dir=install_dir,
                path_prefix=[path_dir],
            )

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("is not on PATH", result.stderr)

    def test_fails_when_no_writable_path_candidate_exists(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmpdir = Path(tmp)
            autoloop_home = self._make_fake_home(tmpdir)
            tool_dir = tmpdir / "tools"
            tool_dir.mkdir()
            for tool in ("bash", "python3"):
                source = shutil.which(tool)
                self.assertIsNotNone(source)
                (tool_dir / tool).symlink_to(source)
            empty_path = tmpdir / "empty-path"
            empty_path.mkdir()

            result = self._run_installer(
                tmpdir,
                autoloop_home=autoloop_home,
                path_prefix=[tool_dir, empty_path],
                set_install_dir=False,
                append_system_path=False,
            )

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("no writable install directory found on PATH", result.stderr)

    def test_fails_when_path_resolves_to_different_autoloop(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            tmpdir = Path(tmp)
            autoloop_home = self._make_fake_home(tmpdir)
            install_dir = tmpdir / "install-bin"
            install_dir.mkdir()
            conflict_dir = tmpdir / "conflict-bin"
            conflict_dir.mkdir()
            conflict = conflict_dir / "autoloop"
            conflict.write_text("#!/usr/bin/env bash\nexit 0\n", encoding="utf-8")
            conflict.chmod(0o755)

            result = self._run_installer(
                tmpdir,
                autoloop_home=autoloop_home,
                install_dir=install_dir,
                path_prefix=[conflict_dir, install_dir],
            )

            self.assertNotEqual(result.returncode, 0)
            self.assertIn("PATH resolves autoloop", result.stderr)


if __name__ == "__main__":
    unittest.main()
