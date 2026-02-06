# R33: Rename Program to Rayniyomi - Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Establish "Rayniyomi" fork identity in documentation while preserving upstream code compatibility.

**Architecture:** Documentation-only changes (T1 low risk). Create naming conventions document, update user-facing docs to use "Rayniyomi", add PR template checklist.

**Tech Stack:** Markdown, Git

---

## Task 1: Create Naming Conventions Document

**Files:**
- Create: `docs/governance/naming-conventions.md`

**Step 1: Create naming conventions document**

```bash
cd /Users/rayyacub/Documents/aniyomi
```

Create file with complete content:

```markdown
# Naming Conventions

This document defines when to use "Rayniyomi" (fork name) vs "Aniyomi" (upstream name) to maintain fork identity while preserving code compatibility.

## Guiding Principle

**User-Facing vs Internal:** If a user or contributor sees it, use "Rayniyomi". If it's internal code structure, keep upstream naming for compatibility.

## When to Use "Rayniyomi"

Use "Rayniyomi" in:

- **Documentation files**
  - README.md
  - CONTRIBUTING.md
  - docs/* (all documentation)

- **GitHub templates**
  - .github/ISSUE_TEMPLATE/*
  - .github/pull_request_template.md
  - .github/CODEOWNERS

- **Release communications**
  - CHANGELOG.md entries
  - Release notes
  - Migration guides

- **User-facing comments**
  - Feature explanations in code comments
  - Public API documentation
  - User-visible error messages (when R34+ implements)

- **Governance documents**
  - ADRs (Architecture Decision Records)
  - Governance policies
  - Sprint boards and planning docs

- **Commit messages**
  - When referencing the fork itself
  - Example: "R33: rename program to rayniyomi in documentation"

## Keep Upstream "Aniyomi" Naming

Preserve "Aniyomi" in:

- **Code structure**
  - Java/Kotlin package names: `eu.kanade.tachiyomi.*`
  - Class names, method names, variable names
  - Module names in build configuration

- **Internal implementation**
  - Internal code comments (implementation details)
  - Private API documentation
  - Technical debt notes

- **Configuration**
  - String resources (until R34+ changes them)
  - Build configurations (until R35+ changes them)
  - Gradle module declarations

- **Git history**
  - References to upstream commits
  - Cherry-pick citations
  - Attribution to upstream developers

## Migration Note

**Why preserve upstream naming in code?**

This fork is named **Rayniyomi** but intentionally retains upstream **Aniyomi** package structures (`eu.kanade.tachiyomi.*`) for merge compatibility.

**Rationale:**
- Minimizes merge conflicts when pulling upstream changes
- Preserves git history and blame information
- Reduces refactoring burden
- Code-level renaming planned for future tickets (R40+) if needed

**Code changes in scope:**
- R34: App display name and launcher icon
- R35: Application ID (for installation isolation)
- R36-R38: Fork-specific service endpoints

## Edge Cases & Examples

### ✅ Correct Usage

**User-facing documentation:**
```markdown
# Rayniyomi

Rayniyomi is a personal fork of Aniyomi...
```

**Code comment (public feature explanation):**
```kotlin
// Rayniyomi extends the download manager with...
```

**ADR document:**
```markdown
# ADR 0001: Rayniyomi Fork Identity
```

**Package name (keep upstream):**
```kotlin
package eu.kanade.tachiyomi.data.download
```

**Internal comment (implementation detail):**
```kotlin
// Uses Aniyomi's original caching logic for compatibility
```

### Review Scenarios

**Scenario 1: PR adds user documentation**
- **Question:** References "Aniyomi" in README
- **Answer:** ❌ Should use "Rayniyomi" (user-facing)

**Scenario 2: PR refactors internal code**
- **Question:** Comments reference "Aniyomi's algorithm"
- **Answer:** ✅ Acceptable (internal technical reference)

**Scenario 3: CHANGELOG entry**
- **Question:** "Fixed bug in Aniyomi downloader"
- **Answer:** ❌ Should be "Fixed bug in Rayniyomi downloader" (user-facing)

**Scenario 4: Code package name**
- **Question:** Should `eu.kanade.tachiyomi.*` be renamed?
- **Answer:** ✅ Keep as-is for upstream compatibility (R33 scope)

## Search & Audit Checklist

### Find "Aniyomi" in user-facing files

```bash
# Search documentation (should be reviewed)
grep -r "Aniyomi" README.md CONTRIBUTING.md docs/ .github/ --exclude-dir=.git

# Find in markdown files only
find . -name "*.md" -not -path "./.git/*" -exec grep -l "Aniyomi" {} \;

# Exclude ADRs (may reference upstream intentionally)
grep -r "Aniyomi" docs/ --exclude="*adr*" --exclude-dir=.git
```

### Review Criteria

For each "Aniyomi" reference found:

1. **Is this user-facing?** (docs, templates, comments)
   - Yes → Should use "Rayniyomi"
   - No → "Aniyomi" is acceptable

2. **Is this technical attribution?** (upstream reference, git history)
   - Yes → Keep "Aniyomi" with context
   - No → Should use "Rayniyomi"

3. **Is this internal code?** (packages, classes, methods)
   - Yes → Keep "Aniyomi"
   - No → Should use "Rayniyomi"

### Acceptable "Aniyomi" References

- `eu.kanade.tachiyomi.*` package names
- `import eu.kanade.tachiyomi.*` statements
- Comments citing upstream: "Based on Aniyomi's implementation"
- ADRs documenting fork decision: "Rayniyomi forks from Aniyomi"
- Git history: "Cherry-pick from aniyomiorg/aniyomi#1234"

## Enforcement

### PR Checklist

All PRs must confirm:
- [ ] Naming conventions followed (use "Rayniyomi" in user-facing text)
- [ ] Code structure preserves upstream naming for compatibility

### Review Process

Reviewers should:
1. Check PR template checklist completed
2. Grep for "Aniyomi" in changed files
3. Verify user-facing text uses "Rayniyomi"
4. Confirm intentional exceptions have context

### Automated Checks (Future)

Potential automation (not in R33 scope):
- CI lint for "Aniyomi" in README.md, CONTRIBUTING.md
- Pre-commit hook for documentation files
- PR comment bot suggesting "Rayniyomi" for user-facing changes

---

**Last updated:** 2026-02-06 (R33)
**Next review:** After R34-R38 (fork compliance) completion
```

**Step 2: Verify file created**

```bash
ls -la docs/governance/naming-conventions.md
wc -l docs/governance/naming-conventions.md
```

Expected: File exists, ~250 lines

**Step 3: Commit**

```bash
git add docs/governance/naming-conventions.md
git commit -m "R33: add naming conventions document

Establishes clear rules for when to use 'Rayniyomi' (user-facing)
vs 'Aniyomi' (internal code). Includes search/audit checklist and
edge case examples.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 2: Update README.md

**Files:**
- Modify: `README.md:7,9-10`

**Step 1: Read current README header**

```bash
head -20 README.md
```

Expected: See "# Aniyomi [App](#)" on line 7

**Step 2: Update README header and description**

Replace line 7:
```markdown
# Aniyomi [App](#)
```

With:
```markdown
# Rayniyomi
```

Replace line 9:
```markdown
### Full-featured player and reader, based on ~~Tachiyomi~~ Mihon.
```

With:
```markdown
### Personal fork of Aniyomi - Full-featured anime player and manga reader

**Rayniyomi** is a personal fork of [Aniyomi](https://github.com/aniyomiorg/aniyomi), based on ~~Tachiyomi~~ Mihon.
```

**Step 3: Verify changes**

```bash
head -15 README.md | grep -E "Rayniyomi|Personal fork"
```

Expected: See "# Rayniyomi" and "Personal fork of Aniyomi"

**Step 4: Commit**

```bash
git add README.md
git commit -m "R33: update README to identify as Rayniyomi fork

- Change header from 'Aniyomi' to 'Rayniyomi'
- Add fork clarification and upstream attribution
- Keep upstream links intact (Discord, downloads)

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 3: Update CONTRIBUTING.md

**Files:**
- Modify: `CONTRIBUTING.md:3-6`

**Step 1: Read current CONTRIBUTING intro**

```bash
head -15 CONTRIBUTING.md
```

Expected: See "Thanks for your interest in contributing to Aniyomi!"

**Step 2: Add Fork Identity section**

After line 3 (after horizontal rule), insert:

```markdown

## Fork Identity

**Rayniyomi** is a personal fork of [Aniyomi](https://github.com/aniyomiorg/aniyomi).

When contributing, please use "Rayniyomi" in user-facing text (documentation, PR descriptions, comments) while keeping code structures compatible with upstream.

**See [Naming Conventions](docs/governance/naming-conventions.md) for detailed guidelines.**

---

```

Update line 5 (now line 12):
```markdown
Thanks for your interest in contributing to Aniyomi!
```

To:
```markdown
Thanks for your interest in contributing to Rayniyomi!
```

**Step 3: Verify changes**

```bash
head -20 CONTRIBUTING.md | grep -E "Rayniyomi|Fork Identity"
```

Expected: See "Fork Identity" section and updated greeting

**Step 4: Commit**

```bash
git add CONTRIBUTING.md
git commit -m "R33: add Fork Identity section to CONTRIBUTING

- Clarify Rayniyomi is fork of Aniyomi
- Link to naming conventions document
- Update contributor greeting

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 4: Update PR Template

**Files:**
- Modify: `.github/pull_request_template.md:38`

**Step 1: Read current PR template**

```bash
cat .github/pull_request_template.md
```

Expected: See sections ending with "Release Notes"

**Step 2: Add naming conventions checklist**

After line 37 (after "Release Notes" section), add:

```markdown

## Checklist
- [ ] Naming conventions followed (use "Rayniyomi" in user-facing text, see [naming conventions](../docs/governance/naming-conventions.md))
```

**Step 3: Verify changes**

```bash
tail -10 .github/pull_request_template.md
```

Expected: See "Checklist" section with naming conventions item

**Step 4: Commit**

```bash
git add .github/pull_request_template.md
git commit -m "R33: add naming conventions to PR template

Adds checklist item for reviewers to verify 'Rayniyomi' used in
user-facing text per naming conventions.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 5: Update CLAUDE.md

**Files:**
- Modify: `CLAUDE.md:386-402`

**Step 1: Read Source of Truth section**

```bash
grep -A 15 "## Source of Truth" CLAUDE.md
```

Expected: See list of primary documents

**Step 2: Add naming conventions reference**

In "Source of Truth" section, add after "AGENTS.md":

```markdown
- `docs/governance/naming-conventions.md` - Fork naming guidelines (Rayniyomi vs Aniyomi)
```

**Step 3: Update fork references**

Search and replace any "aniyomi fork" references to "Rayniyomi fork":

```bash
# Find references
grep -n "aniyomi" CLAUDE.md

# Replace as needed (manual review)
```

Expected changes:
- Line 1: Update header if needed
- "Source of Truth" section: Add naming conventions reference
- Any other references: Use "Rayniyomi fork" consistently

**Step 4: Verify changes**

```bash
grep -E "Rayniyomi|naming-conventions" CLAUDE.md
```

Expected: See "Rayniyomi fork" and naming conventions reference

**Step 5: Commit**

```bash
git add CLAUDE.md
git commit -m "R33: add naming conventions to CLAUDE.md

- Reference naming-conventions.md in Source of Truth
- Ensure consistent use of 'Rayniyomi fork'

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

---

## Task 6: Update AGENTS.md (if needed)

**Files:**
- Modify: `AGENTS.md:1-35`

**Step 1: Check for project name references**

```bash
grep -i "aniyomi" AGENTS.md
```

**Step 2: Update if references found**

If AGENTS.md references the project name, update to "Rayniyomi" in user-facing contexts.

**Step 3: Commit (if changes made)**

```bash
git add AGENTS.md
git commit -m "R33: update project references in AGENTS.md

Use 'Rayniyomi' in user-facing agent instructions.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

**Step 4: Skip if no changes needed**

If AGENTS.md doesn't reference project name, no changes needed.

---

## Task 7: Verification & Audit

**Step 1: Grep audit for "Aniyomi" in user-facing files**

```bash
cd /Users/rayyacub/Documents/aniyomi
grep -r "Aniyomi" README.md CONTRIBUTING.md docs/governance/ .github/ --exclude-dir=.git | grep -v "naming-conventions.md"
```

Expected: No unintentional "Aniyomi" in user-facing text (except ADRs and naming-conventions.md examples)

**Step 2: Verify links work**

```bash
# Check naming conventions link in CONTRIBUTING.md
ls -la docs/governance/naming-conventions.md

# Check naming conventions link in PR template
ls -la docs/governance/naming-conventions.md
```

Expected: File exists at expected path

**Step 3: Manual review of updated files**

```bash
# Review README
head -30 README.md

# Review CONTRIBUTING
head -30 CONTRIBUTING.md

# Review PR template
cat .github/pull_request_template.md

# Review CLAUDE.md changes
grep -A 5 "Source of Truth" CLAUDE.md
```

Expected: All changes are clear and accurate

**Step 4: Check git status**

```bash
git status
git log --oneline -10
```

Expected: Clean working tree, 5-6 commits for R33

---

## Task 8: Final Commit (if needed)

**Step 1: Check for any missed updates**

```bash
git status
```

**Step 2: Add any remaining changes**

If there are uncommitted changes:

```bash
git add <files>
git commit -m "R33: <description>

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>"
```

**Step 3: Verify all commits**

```bash
git log --oneline origin/main..HEAD
```

Expected: 5-6 commits all prefixed with "R33:"

---

## Success Criteria

Before marking R33 complete, verify:

- [ ] `docs/governance/naming-conventions.md` created with complete guidance
- [ ] README.md header says "Rayniyomi" with fork clarification
- [ ] CONTRIBUTING.md has Fork Identity section linking to naming conventions
- [ ] PR template includes naming conventions checklist
- [ ] CLAUDE.md references naming conventions in Source of Truth
- [ ] Grep audit shows no unintentional "Aniyomi" in user-facing text
- [ ] All links resolve correctly
- [ ] All commits follow format: `R33: <summary>`
- [ ] Working tree is clean

---

## Next Steps (After R33)

This ticket blocks:
- R34: Update app display name and launcher icon
- R35: Change applicationId
- R36: Change/disable app update checker
- R37: Fork-owned Firebase config
- R38: Fork-owned ACRA endpoint

These tickets will follow the naming conventions established in R33.
