# Gameplan: R56 Upstream Sync

**Ticket:** R56 (Issue #64)
**Objective:** Sync the fork with the upstream repository (`aniyomiorg/aniyomi`) to ensure we have the latest features and fixes.

## 1. Analysis
- **Current Head:** `ryacub/main` (Fork Default)
- **Upstream Head:** `origin/main` (Aniyomi Default)
- **Status:** Initial checks indicate `ryacub/main` is **26 commits ahead** and **0 commits behind** `origin/main`.
- **Implication:** The fork appears key-current with `origin/main` (last upstream commit ~Nov 2025).

## 2. Plan Steps

### Phase 1: Verification
1.  **Create Branch:** `codex/r56-upstream-sync`
2.  **Fetch & Confirm:** Run `git fetch origin` verbose to ensure no new objects are missing.
3.  **Check Divergence:** Re-run `git rev-list --left-right --count ryacub/main...origin/main`.
4.  **Alternative Branches:** Check if `origin/master` or a release branch like `origin/0.18.2.0` has newer commits than `origin/main`.

### Phase 2: Execution (If Divergence Found)
*Only applicable if we find we ARE behind.*
1.  **Merge Upstream:** `git merge origin/main`
2.  **Resolve Conflicts:**
    - Expect conflicts in: `build.gradle.kts` (app ID), `AndroidManifest.xml` (app name), `CONTRIBUTING.md`.
    - **Strategy:** Keep "Rayniyomi" branding and Governance changes; accept Upstream logic changes.
3.  **Verify:** Run standard verification suite (`./gradlew test`).

### Phase 3: Completion
1.  **No-Op Scenario:** If purely ahead, close ticket as "Up to date".
2.  **Sync Scenario:** Push merge commit, open PR with "Upstream Sync" details.

## 3. Risks
- **Branding Regression:** Merging upstream might reset App ID or Name if not careful.
- **Config Overwrite:** Upstream changes to `build.gradle.kts` might enable Analytics/Crashlytics which we explicitly disabled.

## 4. Next Actions
- Execute Phase 1 checks immediately.
- Report findings.