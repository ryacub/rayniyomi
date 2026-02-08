# ADR 0003: Upstream Branding References

**Status:** Accepted
**Date:** 2026-02-08
**Context:** R62 - Remove stale upstream app-name remnants

## Decision

After auditing the codebase for "Tachiyomi" and "Aniyomi" references, we determined that all user-facing branding has been properly updated to "Rayniyomi" and remaining references are intentionally kept for technical reasons.

## Audit Results

### User-Facing References (Updated)

✅ **Already changed to "Rayniyomi":**
- `app_name` in `i18n/src/commonMain/moko-resources/base/strings.xml`
- Application ID in `app/build.gradle.kts` (`xyz.rayniyomi`)
- README.md and CONTRIBUTING.md references (R40)

### Technical References (Intentionally Kept)

The following references to "Tachiyomi" and "Aniyomi" are **intentionally retained** for compatibility and functionality:

#### 1. Package Names
```kotlin
// KEEP - Changing would break compatibility
package eu.kanade.tachiyomi
```

**Rationale:** These are deeply embedded in the codebase and changing them would:
- Break existing user data/database migrations
- Break extension compatibility
- Require massive refactoring with high risk
- Provide no user-visible benefit

#### 2. Extension Metadata Constants
```kotlin
// KEEP - Required for upstream extension compatibility
private const val EXTENSION_FEATURE = "tachiyomi.animeextension"
private const val METADATA_SOURCE_CLASS = "tachiyomi.animeextension.class"
```

**Rationale:** These match the metadata keys used by upstream Aniyomi extensions. Changing them would break all extension loading.

#### 3. Extension Name Parsing
```kotlin
// KEEP - Parses upstream extension naming format
name = it.name.substringAfter("Aniyomi: ")
```

**Rationale:** Extensions from the upstream repository are named "Aniyomi: Extension Name". This code correctly handles that format.

#### 4. Documentation URLs
```kotlin
// KEEP - Points to upstream documentation
"https://aniyomi.org/docs/guides/troubleshooting/"
```

**Rationale:** These point to the upstream Aniyomi project's documentation, which is still relevant and useful for fork users. Creating duplicate documentation would be wasteful.

#### 5. Compose Preview Themes
```kotlin
// KEEP - Internal developer tool
@Preview
@Composable
private fun Preview() {
    TachiyomiPreviewTheme { ... }
}
```

**Rationale:** This is used for Android Studio preview rendering only, never shown to users.

#### 6. Internal File Names
```kotlin
// KEEP - Internal cache files, not user-visible
val file = context.createFileInCacheDir("aniyomi_restore_error.txt")
```

**Rationale:** These are internal cache/temp file names that users never see.

#### 7. Notification Groups
```kotlin
// KEEP - Changing would break existing user notification settings
const val GROUP_NEW_CHAPTERS = "eu.kanade.tachiyomi.NEW_CHAPTERS"
```

**Rationale:** These IDs are stored in Android system settings. Changing them would reset all user notification preferences.

## Consequences

### Positive
- Clear documentation of what should and shouldn't be changed
- Prevents breaking changes to extension compatibility
- Maintains upstream documentation links
- Preserves user data compatibility

### Neutral
- Internal code references still mention "Tachiyomi"/"Aniyomi"
- Not visible to users, only to developers

### Mitigation
- This ADR documents the reasoning for future contributors
- R40 ensures user-facing branding is correct
- Extension compatibility maintained

## References
- R40: Fork identity and compliance changes
- R62: Remove stale upstream app-name remnants
- [Aniyomi Extensions Repository](https://github.com/aniyomiorg/aniyomi-extensions)
