Looking to report an issue/bug or make a feature request? Please refer to the [README file](https://github.com/ryacub/rayniyomi#readme).

---

Thanks for your interest in contributing to Rayniyomi!

**Note:** Rayniyomi is a fork of [Aniyomi](https://github.com/aniyomiorg/aniyomi). For contributing to the upstream project, see [Aniyomi's contributing guide](https://github.com/aniyomiorg/aniyomi/blob/main/CONTRIBUTING.md).


# Code contributions

Pull requests are welcome!

If you're interested in taking on [an open issue](https://github.com/ryacub/rayniyomi/issues), please comment on it so others are aware.
You do not need to ask for permission nor an assignment.

## Pull Request policy

- Use Conventional Commit PR titles with the ticket ID suffix: `fix: short summary (R123)`.
- Fill out all required sections in the PR template.
- Include verification evidence.
- For user-facing changes, include a concrete Release Notes section.

See current governance docs:
- PR validation: [`.github/workflows/pr_governance.yml`](.github/workflows/pr_governance.yml)
- PR template: [`.github/pull_request_template.md`](.github/pull_request_template.md)
- CI tiering: [`docs/ci-tiering.md`](docs/ci-tiering.md)
- Branding guardrail: [`docs/branding-guardrail.md`](docs/branding-guardrail.md)

## Prerequisites

Before you start, please note that the ability to use following technologies is **required** and that existing contributors will not actively teach them to you.

- Basic [Android development](https://developer.android.com/)
- [Kotlin](https://kotlinlang.org/)

### Tools

- [Android Studio](https://developer.android.com/studio)
- Emulator or phone with developer options enabled to test changes.
- **JDK 17** is required for building and running tests.
  - Recommended: [Eclipse Temurin 17](https://adoptium.net/temurin/releases/?version=17) (matches CI environment)
  - The project uses `GradleJavaVersion.VERSION_17` as defined in `buildSrc/src/main/kotlin/mihon/buildlogic/AndroidConfig.kt`

## Getting help

- For Rayniyomi-specific questions, open an issue in this repository.
- For general development questions, you may also reference [Aniyomi's Discord server](https://discord.gg/F32UjdJZrR).

# Translations

Translations are inherited from upstream Aniyomi. For translation contributions, see [Aniyomi's translation guide](https://aniyomi.org/docs/contribute#translation).


# Forks

Forks are allowed so long as they abide by [the project's LICENSE](https://github.com/aniyomiorg/aniyomi/blob/main/LICENSE).

When creating a fork, remember to:

- To avoid confusion with the main app:
    - Change the app name
    - Change the app icon
    - Change or disable the [app update checker](https://github.com/aniyomiorg/aniyomi/blob/main/app/src/main/java/eu/kanade/tachiyomi/data/updater/AppUpdateChecker.kt)
- To avoid installation conflicts:
    - Change the `applicationId` in [`build.gradle.kts`](https://github.com/aniyomiorg/aniyomi/blob/main/app/build.gradle.kts)
- **Analytics and crash reporting are disabled in this fork** (R37, R38):
    - Firebase Analytics is explicitly disabled via `app/src/main/res/values/firebase_analytics_disabled.xml`
    - ACRA crash reporting is disabled via `app/src/main/res/values/acra_disabled.xml` and commented out in [`app/build.gradle.kts`](app/build.gradle.kts)
    - If you create your own fork and want analytics, you'll need to set up your own Firebase project and configure ACRA endpoints
