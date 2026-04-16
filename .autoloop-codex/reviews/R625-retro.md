# R625 Retro

## What Happened
Initial execution attempts failed because `autoloop` binary was not on PATH and the parent workspace was dirty/behind `origin/main`.

## Root Cause
The execution path assumed global autoloop installation and did not lock worktree/root contract before creating artifacts.

## Process Delta
Use the skill-local runner (`python3 .../autoloop_runner.py`) and run `path-check` before writing any plan/claims/review artifacts.

## Guardrail
For every new ticket, first command block must verify: clean dedicated worktree from `origin/main`, repo-local `.autoloop-codex`, and successful `path-check` output.
