# Plugin Release Checklist

Use this checklist for every plugin release. All items must pass before publishing.

## Pre-Release

- [ ] Version bumped in `lightnovel-plugin/build.gradle.kts` (versionCode and versionName)
- [ ] Code formatted: `./gradlew :lightnovel-plugin:spotlessCheck`
- [ ] Unit tests pass: `./gradlew :lightnovel-plugin:testReleaseUnitTest`
- [ ] Changes committed to main
- [ ] Tag created: `git tag plugin-v<version>`
- [ ] Tag pushed: `git push ryacub main --tags`

## CI Pipeline

- [ ] Build job passed (spotless, assembleRelease, unit tests)
- [ ] Sign job passed (APK signed with PLUGIN_* secrets)
- [ ] Verify job passed (SHA-256, apksigner, package name, version extraction)
- [ ] Publish job passed (manifest generated, draft release created)

## Draft Release Review

- [ ] Release title is correct: "Light Novel Plugin plugin-v<version>"
- [ ] Release body contains version, package name, and checksums
- [ ] APK artifact is attached
- [ ] `lightnovel-plugin-manifest.json` is attached
- [ ] `SHA256SUMS.txt` is attached
- [ ] Manifest JSON is valid (parseable, correct fields)
- [ ] Manifest `package_name` is `xyz.rayniyomi.plugin.lightnovel`
- [ ] Manifest `apk_url` points to the correct release asset
- [ ] Manifest `apk_sha256` matches the APK checksum in SHA256SUMS.txt

## Post-Release Verification

- [ ] Run verification script: `scripts/verify-plugin-release.sh plugin-v<version>`
- [ ] Manifest downloadable from release URL
- [ ] APK downloadable from manifest's apk_url
- [ ] SHA-256 of downloaded APK matches manifest
- [ ] APK signature is valid (apksigner verify)
- [ ] Package name is correct (aapt2 dump packagename)

## First Release Only

- [ ] Extract signing certificate fingerprint from signed APK
- [ ] Update `TRUSTED_PLUGIN_CERT_SHA256` in `LightNovelPluginManager.kt`
- [ ] Create follow-up PR with pinned certificate fingerprints
