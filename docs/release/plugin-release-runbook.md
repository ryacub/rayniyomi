# Light Novel Plugin Release Runbook

## Overview

The plugin release process is automated via `.github/workflows/plugin_release.yml`.
Pushing a tag matching `plugin-v*` triggers the full build-sign-verify-publish pipeline.

## Prerequisites

### GitHub Secrets

The following secrets must be configured in `Settings > Secrets and variables > Actions`:

| Secret | Description |
|--------|-------------|
| `PLUGIN_SIGNING_KEY` | Base64-encoded keystore file |
| `PLUGIN_ALIAS` | Signing key alias |
| `PLUGIN_KEY_STORE_PASSWORD` | Keystore password |
| `PLUGIN_KEY_PASSWORD` | Key password |

To encode the keystore: `base64 -i plugin-keystore.jks | pbcopy`

### First Release

After the first signed release, extract the certificate fingerprint and pin it in
`LightNovelPluginManager.kt`:

```bash
apksigner verify --print-certs lightnovel-plugin-plugin-v0.1.0.apk | grep SHA-256
```

Update `TRUSTED_PLUGIN_CERT_SHA256` with the real fingerprints.

## Creating a Release

### 1. Bump the version

Edit `lightnovel-plugin/build.gradle.kts`:

```kotlin
versionCode = 2       // increment
versionName = "0.2.0" // semver
```

### 2. Create compliance attestation

Create a release attestation and save it as:

`docs/release/attestations/<tag>.json` (example: `docs/release/attestations/plugin-v0.2.0.json`).

Required JSON fields:

```json
{
  "release_tag": "plugin-v0.2.0",
  "reviewed_at_utc": "2026-02-20T00:00:00Z",
  "reviewer": "release-manager",
  "distribution_policy_ack": true,
  "dependency_attribution_ack": true,
  "takedown_path_verified": true,
  "content_handling_ack": true,
  "notes": ""
}
```

`## Compliance Gate (R236-Q)` checklist:
- [ ] Distribution policy acknowledged for this release.
- [ ] Plugin dependency attribution reviewed.
- [ ] Takedown/report contact path verified.
- [ ] Content-handling constraints acknowledged.

The release workflow blocks publishing if the attestation file is missing, malformed, tag-mismatched, or any required ack is false.

### 3. Commit and tag

```bash
git add lightnovel-plugin/build.gradle.kts docs/release/attestations/plugin-v0.2.0.json
git commit -m "chore: bump plugin to v0.2.0"
git tag plugin-v0.2.0
git push ryacub main --tags
```

### 4. Monitor the workflow

The `Plugin Release` workflow will:

1. **Build** - Compile the plugin APK, run spotless and unit tests
2. **Sign** - Sign the APK using the configured keystore
3. **Verify** - Compute SHA-256, verify signature with apksigner, check package name
4. **Compliance** - Validate `docs/release/attestations/<tag>.json` with required acknowledgements
5. **Publish** - Generate manifest JSON, create a draft GitHub release with APK + manifest + checksums

### 5. Review and publish

The release is created as a **draft**. Review the release notes and artifacts, then
click "Publish release" in the GitHub UI.

### 6. Post-release verification

```bash
scripts/verify-plugin-release.sh plugin-v0.2.0
```

## What the Automation Does

### Build Job
- Checks out repo at the tagged commit
- Sets up JDK 17 and Gradle
- Runs `spotlessCheck` on the plugin module
- Builds the release APK
- Runs unit tests

### Sign Job
- Downloads the unsigned APK artifact
- Signs using `r0adkll/sign-android-release` with `PLUGIN_*` secrets
- Renames to `lightnovel-plugin-<tag>.apk`

### Verify Job
- Computes SHA-256 checksum
- Runs `apksigner verify` to confirm valid signature
- Runs `aapt2 dump packagename` to confirm correct package
- Extracts versionCode and versionName from badging

### Publish Job
- Generates `lightnovel-plugin-manifest.json` matching the `LightNovelPluginManifest` schema
- Generates `SHA256SUMS.txt`
- Creates a draft GitHub release with all artifacts

## Manual Verification

If you need to verify a release manually:

```bash
# Download
curl -fsSL -O "https://github.com/ryacub/rayniyomi/releases/download/plugin-v0.2.0/lightnovel-plugin-plugin-v0.2.0.apk"
curl -fsSL -O "https://github.com/ryacub/rayniyomi/releases/download/plugin-v0.2.0/lightnovel-plugin-manifest.json"

# Check SHA-256
sha256sum lightnovel-plugin-plugin-v0.2.0.apk
jq '.apk_sha256' lightnovel-plugin-manifest.json

# Verify signature
apksigner verify --verbose --print-certs lightnovel-plugin-plugin-v0.2.0.apk

# Check package name
aapt2 dump packagename lightnovel-plugin-plugin-v0.2.0.apk
```

## Rollback Procedures

### Scenario: Bad release discovered after publishing

1. **Delete the GitHub release** (or mark as pre-release)
2. **Remove the tag:**
   ```bash
   git tag -d plugin-v0.2.0
   git push ryacub --delete plugin-v0.2.0
   ```
3. **Update the stable manifest URL** to point to the previous good release
   (the `latest/download` URL will automatically resolve to the previous release once deleted)
4. **Set `min_plugin_version_code`** in the next manifest to block the bad version

### Scenario: Signing key compromised

1. **Rotate the keystore** (generate new keystore)
2. **Update GitHub secrets** with the new keystore values
3. **Update `TRUSTED_PLUGIN_CERT_SHA256`** in `LightNovelPluginManager.kt` with new fingerprints
4. **Release new plugin version** signed with the new key
5. **Notify users** to update

## Secret Rotation

### Rotating the signing keystore

1. Generate a new keystore:
   ```bash
   keytool -genkey -v -keystore plugin-keystore-new.jks \
     -keyalg RSA -keysize 2048 -validity 10000 \
     -alias plugin-key
   ```

2. Encode to base64:
   ```bash
   base64 -i plugin-keystore-new.jks | pbcopy
   ```

3. Update GitHub secrets:
   - `PLUGIN_SIGNING_KEY` = new base64 keystore
   - `PLUGIN_ALIAS` = new alias
   - `PLUGIN_KEY_STORE_PASSWORD` = new password
   - `PLUGIN_KEY_PASSWORD` = new key password

4. Extract the new certificate fingerprint from a test build and update
   `TRUSTED_PLUGIN_CERT_SHA256` in `LightNovelPluginManager.kt`.

5. Publish a new plugin release with the new signing key.

**Note:** Key rotation means users must update their plugin. The old plugin will fail
signature verification against the new pinned cert.

## Compatibility Gate (R236-O)

Before publishing a plugin release, compatibility must pass in addition to integrity checks.

### Inputs

- Generated plugin manifest (`lightnovel-plugin-manifest.json`)
- Host compatibility snapshot (`./gradlew -q :app:printLightNovelCompatibilitySnapshot`)

### Gate command

```bash
scripts/verify-plugin-compatibility.sh \
  --manifest lightnovel-plugin-manifest.json \
  --host-version <hostVersionCode> \
  --host-channel stable \
  --expected-api <expectedPluginApiVersion>
```

### Notes

- Unknown `release_channel` values are blocked during release gating.
- `target_host_version <= 0` is treated as unset.
- Exact API matching is required (`plugin_api_version == expected`).
- Channel compatibility:
  - stable host accepts stable plugin only.
  - beta host accepts stable and beta plugins.

### API bump rollout order

1. Release host support and update expected plugin API in host.
2. Update plugin manifest/APK to the new API.
3. Run compatibility matrix + script gates.
4. Promote plugin release only after all checks pass.

### Rollback

1. Stop promotion by failing the compatibility gate.
2. Revert manifest range/API changes or release a corrected plugin manifest.
3. Re-run gates before re-publishing.
