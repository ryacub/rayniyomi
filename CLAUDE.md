# Claude Code Guidelines (Repo Scope)

These guidelines apply to all Claude Code work in this repository.

## Required Process

1. **Prime first:** Use `/aniyomi-prime` skill before ANY work (file edits, commits, PRs)
2. **Choose exactly one ticket** before editing code
3. **Create/use dedicated branch:** `<agent-prefix>/<ticket-id>-<slug>` (default: `codex/<ticket-id>-<slug>`)
4. **Follow workflow:** `.local/LLM_DELIVERY_PLAYBOOK.md`
5. **Implement only in-ticket scope** (no unrelated refactors)
6. **Run verification** appropriate to the ticket risk tier
7. **Perform self-review** before commit
8. **Commit with ticket prefix:** `<ticket-id>: <summary>`
9. **Open/update PR** with compliant title and fully updated description
10. **Update sprint board** notes after work

## Hard Stops

- If ticket ID, acceptance criteria, or dependencies are unclear: **stop and clarify**
- If unexpected repo changes appear: **stop and report** before continuing
- If verification cannot be run: **document exactly** what was skipped and why
- If user requests skipping priming: **explain governance requirement** and prime anyway

## Quality Gates

### Code Quality
- No new `runBlocking` in UI-thread callbacks
- No new top-level `GlobalScope` usage
- No multi-ticket commit unless explicitly planned
- Preserve existing error handling and safety checks

### Documentation Requirements
- PR title must include ticket ID and concise scope: `[R##] imperative summary`
- PR description must include:
  - Scope (done) and Non-goals (not done)
  - Verification commands and outcomes
  - Risk assessment
  - Rollback plan (required for P0/T3)

### Workflow Compliance
- Sprint board updates mandatory after ticket completion
- Branch naming format enforced: `<agent-prefix>/<ticket-id>-<slug>` (see Git Workflow section)
- One ticket per branch (split follow-ups if scope expands)

## Priority Enforcement

**P0 Fork Compliance Tickets (BLOCKING):**
These MUST be completed before any external fork distribution:
- R34: Update app display name and launcher icon to "rayniyomi"
- R35: Change base `applicationId` from `xyz.jmir.tachiyomi.mi`
- R36: Change or disable app update checker endpoints
- R37: Set up fork-owned Firebase config (or explicitly disable)
- R38: Set fork-owned ACRA endpoint (or explicitly disable)

**P0/T3 Requirements:**
- Rollback notes mandatory in commit body
- Explicit regression checks before merge
- Post-merge monitoring plan documented

## Risk Tiers & Verification

### T1 (Low Risk)
- **Criteria:** Localized change, no data migration, no concurrency changes
- **Verification:** Targeted tests + lint/type checks
- **Example:** Documentation updates, simple UI text changes

### T2 (Medium Risk)
- **Criteria:** Multi-file behavior change or API boundary change
- **Verification:** Targeted tests + affected module tests + manual sanity path
- **Example:** R35 (applicationId change), feature additions

### T3 (High Risk)
- **Criteria:** Concurrency/threading, storage/migration, startup/critical flows
- **Verification:** Targeted tests + broader suite + explicit regression checks + rollback validation notes
- **Example:** Threading changes, database migrations, player/reader core logic

## High-Conflict Files (Single Owner)

Only one agent/person should work on these files at a time:
- `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerViewModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/player/PlayerActivity.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/reader/ReaderViewModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/entries/anime/AnimeScreenModel.kt`
- `app/src/main/java/eu/kanade/tachiyomi/ui/entries/manga/MangaScreenModel.kt`

**Before working on these:** Check git log and open PRs to ensure no conflicts.

## Git Workflow

### Branch Naming Convention

**Format:** `<agent-prefix>/<ticket-id>-<short-slug>`

**Components:**
- `<agent-prefix>`: Agent/developer identifier (default: `codex` for LLM work)
- `<ticket-id>`: Ticket ID from sprint board (e.g., `r33`, `r34`)
- `<short-slug>`: Brief description, 2-4 words, kebab-case

**Examples:**
```
codex/r33-rename-program
codex/r35-applicationid-update
claude/r50-download-queue-fix
```

**Rules:**
- One ticket per branch (split follow-ups if scope expands)
- Lowercase recommended for consistency
- Use hyphens, not underscores or spaces
- Keep slug descriptive but concise

### Commit Format
```
<ticket-id>: <imperative summary>

[Optional body with context]

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
```

### Commit Safety Protocol
- NEVER update git config
- NEVER run destructive git commands (push --force, reset --hard) unless explicitly requested
- NEVER skip hooks (--no-verify) unless explicitly requested
- NEVER amend commits that have been pushed to remote
- ALWAYS create NEW commits (avoid --amend except when explicitly requested)

### Pull Request Format
```
[R##] Imperative summary of change

## Scope
- What was done
- What was changed

## Non-goals
- What was NOT done (but might be expected)

## Verification
```bash
# Commands run
./gradlew test
./gradlew lint
```
**Results:** All tests passed, no lint errors

## Risk Assessment
Risk tier: T2 (medium)
Areas affected: [list]

## Rollback Plan
[Required for P0/T3 tickets]
To rollback: git revert <commit-hash>
Impact: [describe]

🤖 Generated with [Claude Code](https://claude.com/claude-code)
```

## Work-in-Progress Limits

- **One active ticket** per Claude session at a time
- **Max two open branches** per human owner
- Do **not start a new P0 or T3 ticket** until current one is in `review` or `done`

## Definition of Ready (DoR)

Before starting a ticket, confirm:
- ✅ Ticket has clear acceptance criteria and non-goals
- ✅ Dependencies are complete or explicitly waived
- ✅ Affected files/components are identified
- ✅ Test strategy is specified (unit/integration/manual)
- ✅ Rollback path is identified (for P0/P1 tickets)

## Definition of Done (DoD)

Before closing a ticket, confirm:
- ✅ Acceptance criteria met and non-goals respected
- ✅ Verification matrix completed for ticket risk tier
- ✅ Self-review completed with no unresolved high-severity findings
- ✅ PR title and description are accurate and up to date
- ✅ Sprint board updated with branch, commit, verification, and follow-ups
- ✅ For user-facing changes: release note entry drafted

## Common Mistakes & Fixes

| Mistake | Fix |
|---------|-----|
| Skipping priming | ALWAYS use `/aniyomi-prime` before ANY work |
| Working on wrong branch | Check git status, create `<agent-prefix>/<ticket-id>-<slug>` |
| Mixing multiple tickets | One ticket per branch, split if scope expands |
| Missing verification | Run verification matrix appropriate to risk tier |
| Forgetting sprint board update | Update status/notes after every ticket |
| Generic commit messages | Use format: `<ticket-id>: <imperative summary>` |
| Missing rollback plan | Required for all P0/T3 tickets in PR description |

## Tool Preferences

### Build & Test
- **Build tool:** Gradle with Kotlin DSL
- **Run tests:** `./gradlew test`
- **Run lint:** `./gradlew lint`
- **Build APK:** `./gradlew assembleDebug`

### Code Quality
- **Format:** Follow existing Kotlin style (EditorConfig configured)
- **Never add:** New `runBlocking` in UI threads, new `GlobalScope` usage
- **Always preserve:** Existing error handling, nullability checks

## Source of Truth

**Primary documents:**
- This file (`CLAUDE.md`) - Claude Code-specific guidelines
- `AGENTS.md` - General agent rules (applies to all agents)
- `docs/governance/naming-conventions.md` - Fork naming guidelines (Rayniyomi vs Aniyomi)
- `.local/LLM_DELIVERY_PLAYBOOK.md` - Detailed workflow process
- `.local/remediation-sprint-board.md` - Current tickets and priorities

**Project docs:**
- `CONTRIBUTING.md` - General contribution guidelines
- `docs/governance/` - Policies, ADRs, and governance framework
- `docs/adr/` - Architecture Decision Records

## Special Notes for Claude Code

### Skills Available
- `/aniyomi-prime` - MANDATORY before any work (loads context efficiently)
- Use superpowers TDD skills for implementation work
- Use debugging skills for issue investigation

### Context Management
- Prime before EVERY session to get current sprint status
- Reference governance docs by path (don't read full files)
- Use grep/head for token efficiency on large files

### When to Ask for Clarification
- Ticket acceptance criteria unclear
- Dependencies not documented
- Risk tier ambiguous
- Verification strategy not specified
- Rollback approach uncertain for P0/T3

**Don't guess - ask the user to clarify requirements before implementation.**

## Emergency Procedures

### If Tests Fail
1. **STOP** - do not commit
2. Read test output carefully
3. Use systematic debugging approach
4. Fix the issue
5. Re-run full verification matrix
6. Only commit when ALL tests pass

### If Build Breaks
1. **STOP** - do not continue work
2. Check for recent changes that might have caused it
3. Consult sprint board for known issues
4. Fix or rollback the breaking change
5. Verify build succeeds before resuming work

### If Blocked by Dependency
1. **STOP** current ticket work
2. Update sprint board: set status to `blocked`
3. Document exact blocker and dependency ticket ID
4. Notify user of blocker
5. Switch to independent ticket if available

---

**Remember:** This is a governed fork with strict quality gates. Following these guidelines prevents rework and ensures consistent, high-quality contributions.
