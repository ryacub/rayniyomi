#!/usr/bin/env bash
set -euo pipefail

AUTOLOOP_HOME="${AUTOLOOP_HOME:-/Users/rayyacub/.config/skillshare/skills/autoloop-codex}"
RUNNER="$AUTOLOOP_HOME/scripts/autoloop_runner.py"
COMMAND_NAME="autoloop"

fail() {
  printf 'install-autoloop-cli: %s\n' "$*" >&2
  exit 1
}

is_on_path() {
  case ":$PATH:" in
    *":$1:"*) return 0 ;;
    *) return 1 ;;
  esac
}

select_install_dir() {
  if [[ -n "${AUTOLOOP_INSTALL_DIR:-}" ]]; then
    printf '%s\n' "$AUTOLOOP_INSTALL_DIR"
    return
  fi

  local candidates=(
    "/opt/homebrew/bin"
    "/usr/local/bin"
    "$HOME/.local/bin"
    "$HOME/bin"
  )

  local candidate
  for candidate in "${candidates[@]}"; do
    if [[ -d "$candidate" && -w "$candidate" && -x "$candidate" ]] && is_on_path "$candidate"; then
      printf '%s\n' "$candidate"
      return
    fi
  done

  fail "no writable install directory found on PATH; set AUTOLOOP_INSTALL_DIR"
}

[[ -f "$RUNNER" ]] || fail "runner not found at $RUNNER"
python3 "$RUNNER" --help >/dev/null || fail "runner help failed at $RUNNER"

INSTALL_DIR="$(select_install_dir)"
mkdir -p "$INSTALL_DIR"
[[ -d "$INSTALL_DIR" && -w "$INSTALL_DIR" ]] || fail "install directory is not writable: $INSTALL_DIR"

WRAPPER="$INSTALL_DIR/$COMMAND_NAME"
cat >"$WRAPPER" <<EOF
#!/usr/bin/env bash
set -euo pipefail
AUTOLOOP_HOME="\${AUTOLOOP_HOME:-$AUTOLOOP_HOME}"
exec python3 "\$AUTOLOOP_HOME/scripts/autoloop_runner.py" "\$@"
EOF
chmod 0755 "$WRAPPER"

if ! is_on_path "$INSTALL_DIR"; then
  fail "installed $WRAPPER, but $INSTALL_DIR is not on PATH"
fi

RESOLVED="$(command -v "$COMMAND_NAME" || true)"
[[ "$RESOLVED" == "$WRAPPER" ]] || fail "PATH resolves $COMMAND_NAME to '${RESOLVED:-<missing>}' instead of $WRAPPER"
"$COMMAND_NAME" --help >/dev/null || fail "$COMMAND_NAME --help failed after install"

printf 'Installed %s\n' "$WRAPPER"
