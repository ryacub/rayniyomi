# R33: Rename Program to Rayniyomi - Design Document

**Date:** 2026-02-06
**Ticket:** R33
**Status:** Approved
**Risk Tier:** T1 (Low - documentation only)

---

## Overview

Update documentation and establish naming conventions to rename the fork from "Aniyomi" to "Rayniyomi" in user-facing contexts while preserving upstream package structures for merge compatibility.

## Approach: Hybrid (User-Facing vs Internal)

**Guiding Principle:** If a user or contributor sees it, use "Rayniyomi". If it's internal code structure, keep upstream naming for compatibility.

### Use "Rayniyomi" in:
- User-facing documentation (README, CONTRIBUTING, docs/)
- GitHub templates (.github/*)
- Release notes and changelogs
- User-visible comments in code
- Governance documents
- Commit messages referencing the fork

### Keep Upstream "Aniyomi" in:
- Java/Kotlin package names (`eu.kanade.tachiyomi.*`)
- Class names, method names, variable names
- Internal code comments (implementation details)
- String resources (until R34+ changes them)
- Build configurations (until R35+ changes them)
- Git history references

**Migration Note:** "This fork is named Rayniyomi but retains upstream Aniyomi package structures for merge compatibility."

---

## Deliverables

### 1. Naming Conventions Document

**Location:** `docs/governance/naming-conventions.md`

**Content Structure:**
- Header & Purpose
- Section 1: When to Use "Rayniyomi"
- Section 2: Keep Upstream "Aniyomi" Naming
- Section 3: Edge Cases & Examples
- Section 4: Migration Note
- Section 5: Search & Audit Checklist

**Purpose:** Single source of truth for naming decisions, referenced from CONTRIBUTING.md and PR template.

### 2. Documentation Updates

**Files to Modify:**

#### `README.md`
- **Line 7:** `# Aniyomi [App](#)` → `# Rayniyomi`
- **Line 9:** Add fork clarification: `Rayniyomi is a personal fork of Aniyomi.`
- Update description to clarify fork status
- Keep upstream links intact (Discord, downloads)

#### `CONTRIBUTING.md`
- Add "Fork Identity" section after line 3
- Link to naming conventions document
- Brief explanation of "Rayniyomi" vs "Aniyomi" usage

#### `.github/pull_request_template.md`
- Add checklist item:
  ```markdown
  - [ ] Naming conventions followed (use "Rayniyomi" in user-facing text, see [naming conventions](../docs/governance/naming-conventions.md))
  ```

#### `CLAUDE.md`
- Add reference to naming conventions in "Source of Truth" section
- Ensure all references say "Rayniyomi fork"

#### `AGENTS.md` (if applicable)
- Update any project name references

### 3. Search & Audit Checklist

**Commands to include in naming-conventions.md:**

```bash
# Find "Aniyomi" in documentation (should be reviewed)
grep -r "Aniyomi" README.md CONTRIBUTING.md docs/ .github/ --exclude-dir=.git

# Find in markdown files only
find . -name "*.md" -not -path "./.git/*" -exec grep -l "Aniyomi" {} \;

# Exclude expected cases (ADRs referencing upstream)
grep -r "Aniyomi" docs/ --exclude="*adr*" --exclude-dir=.git
```

**Review Criteria:**
- User-facing context? → Should use "Rayniyomi"
- Internal/technical reference? → "Aniyomi" is acceptable
- Upstream attribution? → Keep "Aniyomi" with context

---

## Implementation Steps

### Branch
```bash
git checkout -b codex/r33-rename-program
```

### Changes (in order)

1. **Create `docs/governance/naming-conventions.md`** (new file)
   - Full content with all sections
   - Search & audit commands
   - Examples and edge cases

2. **Update `README.md`**
   - Change header to "Rayniyomi"
   - Add fork clarification
   - Verify links still valid

3. **Update `CONTRIBUTING.md`**
   - Add Fork Identity section
   - Link to naming conventions

4. **Update `.github/pull_request_template.md`**
   - Add naming conventions checklist item

5. **Update `CLAUDE.md`**
   - Add naming conventions to Source of Truth
   - Update fork references

6. **Update `AGENTS.md`** (if needed)
   - Update project name references

### Verification (T1 - Low Risk)

**Required checks:**
- [ ] All markdown files lint correctly
- [ ] Grep audit shows intentional "Aniyomi" usage only
- [ ] Manual review: updated docs are clear and accurate
- [ ] Links in docs resolve correctly
- [ ] No broken references

**Commands:**
```bash
# Markdown linting (if markdownlint installed)
markdownlint README.md CONTRIBUTING.md docs/governance/naming-conventions.md

# Audit for "Aniyomi" in user-facing files
grep -r "Aniyomi" README.md CONTRIBUTING.md docs/governance/ .github/ --exclude-dir=.git
```

### Commit Message
```
R33: rename program to rayniyomi in documentation

- Create docs/governance/naming-conventions.md with clear rules
- Update README.md header and description for fork identity
- Add Fork Identity section to CONTRIBUTING.md
- Add naming conventions checklist to PR template
- Update CLAUDE.md with naming conventions reference

Establishes "Rayniyomi" for user-facing text while preserving
upstream package names for compatibility. Code-level changes
in R34-R38.

Co-Authored-By: Claude Sonnet 4.5 <noreply@anthropic.com>
```

---

## Non-Goals (Out of Scope for R33)

- ❌ Changing app name/icon in code (R34)
- ❌ Changing applicationId (R35)
- ❌ Changing package names in code
- ❌ Updating string resources
- ❌ Modifying build configurations

These are handled in subsequent tickets (R34-R40).

---

## Edge Cases & Examples

### Correct Usage

✅ **README header:** "# Rayniyomi"
✅ **Code comment (user-facing):** "Rayniyomi extends download manager with..."
✅ **ADR title:** "Rayniyomi Fork Identity"
✅ **Package name:** `eu.kanade.tachiyomi.data` (keep as-is)
✅ **Internal comment:** `// Uses Aniyomi's original caching logic`

### Review Scenarios

**Scenario 1:** PR adds documentation for new feature
**Question:** Does it say "Aniyomi" or "Rayniyomi"?
**Answer:** Should say "Rayniyomi" for user-facing docs

**Scenario 2:** PR refactors internal code with comments
**Question:** Internal comments reference "Aniyomi"
**Answer:** Acceptable - internal technical reference

**Scenario 3:** CHANGELOG entry
**Question:** "Fixed bug in Aniyomi downloader"
**Answer:** Should be "Fixed bug in Rayniyomi downloader" (user-facing)

---

## Dependencies

**Depends on:** R00 (completed)
**Blocks:** R34-R38 (fork compliance tickets)

R33 establishes the naming conventions that R34-R38 will follow when making code-level changes.

---

## Success Criteria

- [ ] `docs/governance/naming-conventions.md` created with complete guidance
- [ ] README.md clearly identifies project as "Rayniyomi"
- [ ] CONTRIBUTING.md links to naming conventions
- [ ] PR template includes naming conventions checklist
- [ ] All updated docs pass markdown linting
- [ ] Grep audit shows no unintentional "Aniyomi" in user-facing text
- [ ] Documentation is clear and unambiguous for future contributors

---

**Design approved by:** User
**Ready for implementation:** Yes
