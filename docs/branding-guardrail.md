# Branding Guardrail

This repository enforces a PR-time guardrail that scans added lines for upstream branding regressions in user-facing surfaces.

## What is checked

- Added lines only from `git diff <base>...HEAD` (deterministic, diff-only)
- Scoped files:
  - `app/src/main/**`
  - `i18n/src/commonMain/moko-resources/base/strings.xml`
  - `i18n/src/commonMain/moko-resources/base/plurals.xml`
  - `README*`
  - `docs/**`
- Excluded files:
  - `.github/**`
  - compatibility `strings-<legacy>.xml` resources
  - compatibility `plurals-<legacy>.xml` resources

## Fix flow

1. Replace the upstream-brand token in user-facing content with Rayniyomi branding.
2. Re-run `python3 scripts/check_branding_guardrail.py --base origin/main`.
3. If the match is intentional compatibility/attribution, add a narrow allowlist rule.

## Allowlist format

Use `scripts/branding_guardrail_allowlist.txt` with one tab-separated rule per line:

`<path_regex>\t<token_regex>\t<reason>`

Rules must be narrow and path-specific. Do not add wildcard catch-all exemptions.

## Review rule for allowlist edits

If a PR modifies `scripts/branding_guardrail_allowlist.txt`, PR body must contain a section titled:

`## Branding Guardrail Exceptions`

Include why the exception is required and why a narrower rule was not possible.
