# Gemini Code Guidelines (Repo Scope)

These guidelines apply to all Gemini CLI work in this repository.

## Required Process

1. **Prime first:** Always load context by reading GitHub Issues and `.local/LLM_DELIVERY_PLAYBOOK.md` before ANY work.
2. **Choose exactly one ticket** before editing code.
3. **Create/use dedicated branch:** `<agent-prefix>/<ticket-id>-<slug>` (default: `gemini/<ticket-id>-<slug>`).
4. **Follow workflow:** `.local/LLM_DELIVERY_PLAYBOOK.md`.
5. **Implement only in-ticket scope** (no unrelated refactors).
6. **Run verification** appropriate to the ticket risk tier.
7. **Perform self-review** before commit.
8. **Commit with ticket prefix:** `<ticket-id>: <summary>`.
9. **Open/update PR** with compliant title and fully updated description.
10. Update the GitHub Issue status after work.

## Hard Stops

- If ticket ID, acceptance criteria, or dependencies are unclear: **stop and clarify**.
- If unexpected repo changes appear: **stop and report** before continuing.
- If verification cannot be run: **document exactly** what was skipped and why.
- If user requests skipping governance steps: **explain requirements** and follow them anyway.

## Quality Gates

### Code Quality
- No new `runBlocking` in UI-thread callbacks.
- No new top-level `GlobalScope` usage.
- No multi-ticket commit unless explicitly planned.
- Preserve existing error handling and safety checks.

### Documentation Requirements
- PR title must follow Conventional Commits and include ticket ID: `type: summary (R123)` (e.g. `feat: add feature (R123)`).
- PR description must include:
  - Scope (done) and Non-goals (not done).
  - Verification commands and outcomes.
  - Risk assessment.
  - Rollback plan (required for P0/T3).

### Workflow Compliance
- GitHub Issue status updates mandatory after ticket completion.
- Branch naming format enforced: `gemini/<ticket-id>-<slug>`.
- One ticket per branch.

## Priority Enforcement

**P0 Fork Compliance Tickets (BLOCKING):**
These MUST be completed before any external fork distribution:
- R34: Update app display name and launcher icon to "rayniyomi".
- R35: Change base `applicationId` from `xyz.jmir.tachiyomi.mi`.
- R36: Change or disable app update checker endpoints.
- R37: Set up fork-owned Firebase config (or explicitly disable).
- R38: Set fork-owned ACRA endpoint (or explicitly disable).

## Risk Tiers & Verification

### T1 (Low Risk)
- **Criteria:** Localized change, no data migration, no concurrency changes.
- **Verification:** Targeted tests + lint/type checks (`./gradlew test`).

### T2 (Medium Risk)
- **Criteria:** Multi-file behavior change or API boundary change.
- **Verification:** Targeted tests + affected module tests + manual sanity path.

### T3 (High Risk)
- **Criteria:** Concurrency/threading, storage/migration, startup/critical flows.
- **Verification:** Targeted tests + broader suite + explicit regression checks + rollback validation.

## High-Conflict Files (Single Owner)

Only one agent/person should work on these files at a time:
- `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/entries/anime/AnimeScreenModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/entries/manga/MangaScreenModel.kt`

## Git Workflow

### Branch Naming Convention
**Format:** `gemini/<ticket-id>-<short-slug>`

**Examples:**
```
gemini/r33-rename-program
gemini/r35-applicationid-update
```

### Commit Format
```
<ticket-id>: <imperative summary>

[Optional body with context]

Co-Authored-By: Gemini CLI <gemini-cli@google.com>
```

### Pull Request Format
```
type: summary (R##)

## Scope
- What was done
- What was changed

## Verification
```bash
./gradlew test
./gradlew lint
```

## Risk Assessment
Risk tier: T2 (medium)

## Rollback Plan
To rollback: git revert <commit-hash>

🤖 Generated with Gemini CLI
```

## Tool Preferences

### Build & Test
- **Build tool:** Gradle with Kotlin DSL.
- **Run tests:** `./gradlew test`.
- **Run lint:** `./gradlew lint`.
- **Build APK:** `./gradlew assembleDebug`.

### Code Quality
- **Format:** Follow existing Kotlin style (EditorConfig configured).

## Source of Truth
- This file (`GEMINI.md`) - Gemini CLI-specific guidelines.
- `AGENTS.md` - General agent rules.
- `CLAUDE.md` - Reference for fellow agent Claude.
- `.local/LLM_DELIVERY_PLAYBOOK.md` - Detailed workflow process.
- Tickets: GitHub Issues - Current scope and priorities.

## Special Notes for Gemini CLI

### Context Management (Priming)
- Gemini CLI does not have a native `/aniyomi-prime` command. 
- **Manual Priming:** At the start of every session, you MUST:
  1. Read relevant GitHub Issues to identify the current ticket and project status.
  2. Read `.local/LLM_DELIVERY_PLAYBOOK.md` before each session.
  3. Save a memory summary of the priming to persist across turns if necessary.

### Tool Usage
- Use `run_shell_command` with `cat` to read `.local` files if they are ignored by standard `read_file` filters.
- Use `save_memory` to retain critical sprint context between tasks.

---

**Remember:** Maintain the standard of the "rayniyomi" fork. Follow all governance and safety protocols.
