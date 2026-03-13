# Implementation Plan: Encrypted Shared Preferences for PIN Hash and Tracker Tokens (R440)

## Overview
Migrate PIN hash and rayniyomi-specific tracker API tokens from plaintext SharedPreferences to encrypted storage using EncryptedSharedPreferences. Create a new `RayniyomiSecurePrefs` singleton that manages sensitive data independently, with migration logic to move existing plaintext data and safely delete old keys.

## Stack
- Language: Kotlin 1.9+
- Test framework: JUnit 5 (Jupiter) + MockK + Kotest assertions
- Test command: `./gradlew :app:testDebugUnitTest --tests "*EncryptedPrefsTest*"`

## Features (implement in this order)

1. **Add androidx.security:security-crypto dependency** — Add encrypted preferences library to gradle
   - Test file: None (configuration only)
   - Success: Build succeeds, dependency available for import
   - Independent: yes

2. **Create RayniyomiSecurePrefs singleton** — Wrapper around EncryptedSharedPreferences for PIN hash and tracker tokens
   - Test file: `app/src/test/java/eu/kanade/tachiyomi/security/RayniyomiSecurePrefsTest.kt`
   - Success: Can store/retrieve PIN hash and tracker tokens from encrypted store with correct key names
   - Independent: yes

3. **Implement PIN hash migration** — Read from plaintext `pin_hash` → write to encrypted store → delete old key
   - Test file: `app/src/test/java/eu/kanade/tachiyomi/security/PinHashMigrationTest.kt`
   - Success: Existing PIN hash moves to encrypted store; old key deleted; idempotent (safe to run multiple times)
   - Independent: yes

4. **Implement tracker token migration** — Read tracker tokens from plaintext → write to encrypted store → delete old keys
   - Test file: `app/src/test/java/eu/kanade/tachiyomi/security/TrackerTokenMigrationTest.kt`
   - Success: All rayniyomi tracker tokens migrate to encrypted store; old keys deleted; only rayniyomi tokens migrate (aniyomi tokens stay untouched)
   - Independent: yes

5. **Initialize RayniyomiSecurePrefs in App.onCreate** — Call init() before any security operations
   - Test file: None (integration tested via existing tests)
   - Success: App starts without crashes; preferences initialized early in lifecycle
   - Independent: no (depends on feature 2)

6. **Update SecurityPreferences to use RayniyomiSecurePrefs** — PIN hash reads/writes delegate to encrypted store
   - Test file: `app/src/test/java/eu/kanade/tachiyomi/core/security/SecurityPreferencesIntegrationTest.kt`
   - Success: `pinHash()` getter/setter use encrypted store; backward compatible with existing code
   - Independent: no (depends on features 2-3)

7. **Update TrackPreferences to use RayniyomiSecurePrefs for tracker tokens** — Delegate to encrypted store for rayniyomi tokens
   - Test file: `app/src/test/java/eu/kanade/domain/track/service/TrackPreferencesIntegrationTest.kt`
   - Success: Tracker token getters/setters use encrypted store; backward compatible; aniyomi tokens untouched
   - Independent: no (depends on features 2-4)

## Completion Criteria
- All tests passing:
  - `RayniyomiSecurePrefsTest.kt` — Core encryption/decryption
  - `PinHashMigrationTest.kt` — PIN hash migration
  - `TrackerTokenMigrationTest.kt` — Tracker token migration
  - `SecurityPreferencesIntegrationTest.kt` — PIN hash integration
  - `TrackPreferencesIntegrationTest.kt` — Tracker token integration
- No regressions: `./gradlew :app:testDebugUnitTest` fully passes
- Code format: `./gradlew :app:spotlessApply`
- No fully qualified class names: `grep -r "= [a-z]*\.[a-z]*\.[a-z]*\." app/src/`

## Edge Cases

### RayniyomiSecurePrefs core (Feature 2)
- **Empty/null values:** Storing `null` → retrieving as `null`; storing empty string → retrieving as empty string
- **KeyStore failures:** AndroidKeyStore not available on some test environments (Robolectric)
- **First-time initialization:** MasterKey creation succeeds on first call; subsequent calls reuse existing key
- **Concurrent access:** Multiple threads reading/writing simultaneously (prefs are thread-safe via SharedPreferences locking)

### PIN hash migration (Feature 3)
- **First launch with existing plaintext PIN:** Old value read → encrypted store written → old key deleted
- **Fresh install (no old PIN):** Empty plaintext value → nothing migrated (skipped)
- **Already migrated:** Old key already deleted → migration skips, uses encrypted store
- **Partial migration:** If migration partially failed, running again is safe (idempotent)
- **Both old and new keys present:** Encrypted takes precedence; old key cleaned up on next run

### Tracker token migration (Feature 4)
- **Multiple tracker tokens:** All rayniyomi tokens (if any) migrate in one pass
- **No rayniyomi tokens:** Aniyomi tracker tokens (username, password, auth_expired) NOT migrated (untouched)
- **Identifying rayniyomi tokens:** Only keys matching rayniyomi patterns (to be defined — e.g., keys NOT in core aniyomi trackers list)
- **Empty token values:** Empty strings handled correctly (not migrated as "missing")
- **Already migrated:** Old keys deleted → skip on subsequent runs

### Security/integration (Features 5-7)
- **App initialization order:** RayniyomiSecurePrefs must init before PIN validation code runs
- **Test environment limitations:** Robolectric unit tests cannot access real AndroidKeyStore; must use test doubles/mocking for KeyStore operations
- **Backward compatibility:** Existing PIN validation logic unchanged; TrackPreferences API unchanged
- **Null/empty handling:** PIN hash "" (empty) is valid; tracker tokens "" (empty) are valid

## Risks & Notes

### Dependency Risk
- `androidx.security:security-crypto` NOT currently in `gradle/libs.versions.toml`
- Must add to `libs.versions.toml` and `app/build.gradle.kts`
- Library version: recommend latest stable (currently 1.1.0-alpha06 or later)

### KeyStore & Testing Risk
- Real AndroidKeyStore unavailable in Robolectric unit tests
- **Mitigation:** Use `mockk` to mock MasterKey.Builder; test encryption/decryption logic separately from KeyStore integration
- Integration tests (with real KeyStore) must run on Android device or instrumented tests
- Suggest: Unit tests mock KeyStore; separate instrumented test if full E2E needed

### Initialization & Timing
- App.onCreate must call `RayniyomiSecurePrefs.init(context)` BEFORE any code that reads/writes PIN hash or tokens
- Current `App.kt` already has onCreate; add init call there
- No circular dependency risks (prefs don't depend on other major modules)

### Migration Safety
- Migrations must be idempotent — safe to run multiple times without data loss
- **Approach:** Check if encrypted key exists before migrating; only delete old plaintext key after encrypted key confirmed written
- Test with scenarios: fresh install, existing user, already-partially-migrated

### Rooted Device Vulnerability
- Rooted devices can read EncryptedSharedPreferences if KeyStore compromise
- EncryptedSharedPreferences uses AES-256-GCM (spec from issue: `AES256_SIV, AES256_GCM`)
- This is the Android best-practice solution; better than plaintext but not immune to root access
- Document as: "Protects from offline/passive attacks; not effective vs. active rooted compromise"

### Rayniyomi Token Identification
- **Challenge:** TrackPreferences has aniyomi-managed tracker credentials (username, password) — do NOT migrate these
- **Solution:** Define list of rayniyomi-specific trackers or key patterns
- Example: If rayniyomi only uses tracker tokens (not username/password), migrate only keys matching `track_token_` pattern (not `pref_mangasync_*`)
- **Current finding:** TrackPreferences shows:
  - Aniyomi keys: `pref_mangasync_username_${tracker.id}`, `pref_mangasync_password_${tracker.id}`, `pref_tracker_auth_expired_${tracker.id}`
  - Tokens: `track_token_${tracker.id}`
- **Migration plan:** Migrate only `track_token_*` keys to secure store; leave `pref_mangasync_*` and `pref_tracker_auth_*` untouched in plaintext SharedPreferences

### Scope Constraints
- Do NOT change aniyomi tracker credential storage (username/password remain in plaintext SharedPreferences)
- Do NOT touch other security-related keys (use_biometric_lock, lock_app_after, etc.)
- Only migrate: `pin_hash`, `pin_salt` (if present), and `track_token_*` keys

## File Locations

### New Implementation Files
- `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/eu/kanade/tachiyomi/security/RayniyomiSecurePrefs.kt`
- `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/eu/kanade/tachiyomi/security/PinHashMigration.kt`
- `/Users/rayyacub/Documents/rayniyomi/app/src/main/java/eu/kanade/tachiyomi/security/TrackerTokenMigration.kt`

### New Test Files
- `/Users/rayyacub/Documents/rayniyomi/app/src/test/java/eu/kanade/tachiyomi/security/RayniyomiSecurePrefsTest.kt`
- `/Users/rayyacub/Documents/rayniyomi/app/src/test/java/eu/kanade/tachiyomi/security/PinHashMigrationTest.kt`
- `/Users/rayyacub/Documents/rayniyomi/app/src/test/java/eu/kanade/tachiyomi/security/TrackerTokenMigrationTest.kt`
- `/Users/rayyacub/Documents/rayniyomi/app/src/test/java/eu/kanade/tachiyomi/core/security/SecurityPreferencesIntegrationTest.kt`
- `/Users/rayyacub/Documents/rayniyomi/app/src/test/java/eu/kanade/domain/track/service/TrackPreferencesIntegrationTest.kt`

### Files to Modify
- `app/build.gradle.kts` — Add androidx.security:security-crypto dependency
- `gradle/libs.versions.toml` — Add security-crypto version
- `app/src/main/java/eu/kanade/tachiyomi/App.kt` — Initialize RayniyomiSecurePrefs in onCreate
- `app/src/main/java/eu/kanade/tachiyomi/core/security/SecurityPreferences.kt` — Delegate pinHash() to RayniyomiSecurePrefs
- `app/src/main/java/eu/kanade/domain/track/service/TrackPreferences.kt` — Delegate trackToken() to RayniyomiSecurePrefs

