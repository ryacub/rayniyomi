Looking to report an issue/bug or make a feature request? Please refer to the [README file](https://github.com/aniyomiorg/aniyomi#issues-feature-requests-and-contributing).

---

Thanks for your interest in contributing to Aniyomi!


# Code contributions

Pull requests are welcome!

If you're interested in taking on [an open issue](https://github.com/aniyomiorg/aniyomi/issues), please comment on it so others are aware.
You do not need to ask for permission nor an assignment.

## Pull Request policy

- Use ticket-based PR titles: `[R123] short imperative summary`
- Fill out all required sections in the PR template.
- Include verification evidence and risk/rollback notes.
- For `P0` or `T3` changes, include `breaking-change` and `rollback-tested` labels.
- For user-facing changes, include a concrete Release Notes section.

See governance docs:
- [`docs/governance/agent-workflow.md`](docs/governance/agent-workflow.md)
- [`docs/governance/branch-protection.md`](docs/governance/branch-protection.md)
- [`docs/governance/dependency-policy.md`](docs/governance/dependency-policy.md)
- [`docs/governance/labels-policy.md`](docs/governance/labels-policy.md)
- [`docs/governance/release-notes-policy.md`](docs/governance/release-notes-policy.md)
- [`docs/governance/rollback-drills.md`](docs/governance/rollback-drills.md)
- [`docs/governance/slo-policy.md`](docs/governance/slo-policy.md)
- ADRs: [`docs/adr/`](docs/adr/)

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

- Join [the Discord server](https://discord.gg/F32UjdJZrR) for online help and to ask questions while developing.

# Translations

Translations are done externally via Weblate. See [our website](https://aniyomi.org/docs/contribute#translation) for more details.


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
