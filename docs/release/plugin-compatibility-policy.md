# Light Novel Plugin Compatibility Policy

## Purpose

This policy defines the compatibility contract between the Rayniyomi host app and the
light novel plugin. It is enforced by unit-test matrix coverage and CI release gates.

## Compatibility Dimensions

### 1) Plugin API version

Policy: **exact match only**.

- The plugin manifest field `plugin_api_version` must equal host expected API.
- Current expected API source of truth is `BuildConfig.LIGHT_NOVEL_PLUGIN_API_VERSION`.

### 2) Host version range

Manifest fields:
- `min_host_version` (required lower bound)
- `target_host_version` (optional upper bound)

Semantics:
- `target_host_version = null` means no upper bound.
- `target_host_version <= 0` is treated as unset (same as null).

### 3) Release channel policy

Allowed channels:
- `stable`
- `beta`

Host acceptance:
- stable host accepts stable plugin only.
- beta host accepts stable and beta plugin.

Runtime behavior for unknown channel strings coerces to stable. Release compatibility gate
is stricter and fails unknown values to prevent accidental promotion.

## API Bump Rollout Order

When introducing plugin API `N+1`:

1. Merge host changes that support API `N+1` and release host build with updated expected API.
2. Update plugin manifest/APK to API `N+1`.
3. Run compatibility matrix and release gate checks.
4. Promote plugin release only after gates pass.

Do not publish a plugin API bump before the host release that expects it.

## Enforcement

- Unit test matrix: `app/src/test/resources/novel/plugin_compatibility_matrix.json`
- Matrix test: `PluginCompatibilityMatrixTest`
- CI script gate: `scripts/verify-plugin-compatibility.sh`
- CI workflows:
  - PR: `.github/workflows/plugin_compatibility.yml`
  - Release: `.github/workflows/plugin_release.yml`

## Rollback Guidance

If a compatibility regression escapes:

1. Block promotion by failing compatibility gate (or temporarily disable publish stage).
2. Restrict plugin distribution to known-good range via manifest values.
3. Revert host compatibility wiring if needed.
4. Publish fixed manifest/plugin and update matrix cases to cover the incident.

## Related Tickets

- R236-O: host-plugin compatibility governance and matrix gate
- R236-P: release automation and manifest integrity
- R236-J: update policy and staged rollout controls
