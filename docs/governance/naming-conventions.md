# Naming Conventions: Rayniyomi vs Aniyomi

**Version:** 1.0.0
**Last Updated:** 2026-02-06
**Status:** Active

---

## Purpose

Establish clear rules for when to use **"Rayniyomi"** (user-facing brand) vs **"Aniyomi"** (internal code/upstream references) to maintain consistency and avoid confusion.

---

## Core Principle

**Simple Rule:** Use **"Rayniyomi"** where users see it. Use **"Aniyomi"** everywhere else.

```
USER-FACING     → Rayniyomi
CODE/INTERNAL   → Aniyomi
```

---

## The Rules

### Use "Rayniyomi" (User-Facing)

| Location | Example | Rationale |
|----------|---------|-----------|
| **App Name** | App title bar, launcher icon | User sees this daily |
| **README.md** | Project title, description | First impression for new users |
| **UI Strings** | Settings screens, error messages | Direct user interaction |
| **Release Notes** | Changelog, version descriptions | Public communication |
| **Marketing** | Website, social media, blog posts | Brand identity |
| **User Docs** | Help pages, tutorials, FAQs | User-facing documentation |
| **About Screen** | App info, credits | User-visible metadata |
| **Error Messages** | "Rayniyomi encountered an error" | User-facing feedback |

### Use "Aniyomi" (Internal/Code)

| Location | Example | Rationale |
|----------|---------|-----------|
| **Package Names** | `eu.kanade.tachiyomi` | Maintains upstream compatibility |
| **Class Names** | `AniyomiRepository`, `AniyomiService` | Code-level naming |
| **File Paths** | `src/aniyomi/core/` | Filesystem organization |
| **Database Names** | `aniyomi.db`, `aniyomi_backup` | Internal storage |
| **API Endpoints** | `/api/aniyomi/v1/` | Backend references |
| **Git Branches** | `feature/aniyomi-sync` | Development workflow |
| **Build Artifacts** | `aniyomi-release.apk` (internal) | Internal build process |
| **Code Comments** | `// Aniyomi-specific logic` | Developer documentation |
| **Internal Logs** | `[Aniyomi] Starting service...` | Debug/trace output |
| **Developer Docs** | ARCHITECTURE.md, CONTRIBUTING.md | Technical documentation |
| **Upstream Refs** | `// From Aniyomi upstream` | Attribution/tracking |

---

## Edge Cases

### Mixed Contexts

**Problem:** Some locations are both user-facing AND code-related.

**Solution:** Prioritize user visibility.

| Location | Use | Reason |
|----------|-----|--------|
| **GitHub Repo Name** | `Rayniyomi` | Public-facing URL |
| **APK Filename (Download)** | `Rayniyomi-v1.2.3.apk` | User downloads this |
| **APK Filename (CI)** | `aniyomi-debug.apk` | Internal artifact |
| **Issue Templates** | `Rayniyomi Issue Template` | User-facing form |
| **PR Templates** | Can use either | Internal workflow (prefer Aniyomi) |
| **Commit Messages** | Use "Rayniyomi" for feat/fix, "Aniyomi" for internal | Depends on scope |

### Documentation Split

| Document Type | Use | Example |
|---------------|-----|---------|
| **User Guides** | Rayniyomi | "Getting Started with Rayniyomi" |
| **Developer Guides** | Aniyomi | "Aniyomi Architecture Overview" |
| **Governance Docs** | Either (prefer Rayniyomi) | "Rayniyomi Release Policy" |
| **ADRs** | Aniyomi | "ADR-001: Aniyomi Fork Strategy" |

---

## Migration Strategy

### Phase 1: Low-Hanging Fruit (Week 1)
- [ ] Update README.md title/description
- [ ] Update app name in UI strings
- [ ] Update About screen
- [ ] Update release notes template

### Phase 2: Documentation (Week 2)
- [ ] Audit all user-facing docs
- [ ] Update issue/PR templates
- [ ] Update GitHub repo description
- [ ] Update social media links

### Phase 3: Code Comments (Week 3+)
- [ ] Audit code comments (low priority)
- [ ] Update misleading references only
- [ ] Leave upstream attribution as "Aniyomi"

**Note:** Do NOT rename package names, class names, or file paths (breaks compatibility).

---

## Search & Replace Audit

### Safe Replacements (User-Facing)

```bash
# Find all user-facing "Aniyomi" references
grep -r "Aniyomi" app/src/main/res/values/strings.xml
grep -r "Aniyomi" README.md
grep -r "Aniyomi" docs/user-guides/
grep -r "Aniyomi" .github/ISSUE_TEMPLATE/
```

### DO NOT Replace (Code/Internal)

```bash
# These should stay "Aniyomi"
grep -r "package.*aniyomi" app/src/
grep -r "class.*Aniyomi" app/src/
grep -r "// From Aniyomi" app/src/
```

---

## Examples

### ✅ Good Examples

**User-Facing:**
```markdown
# README.md
# Rayniyomi
The personal anime/manga reader fork of Aniyomi.

Download the latest Rayniyomi release here.
```

**Code:**
```kotlin
// AniyomiRepository.kt
class AniyomiRepository {
    // Aniyomi-specific sync logic
}
```

**Mixed:**
```yaml
# .github/ISSUE_TEMPLATE/bug_report.yml
name: Rayniyomi Bug Report
about: Report a bug in Rayniyomi
labels: ["bug", "aniyomi-fork"]
```

### ❌ Bad Examples

**User-Facing (Bad):**
```markdown
# README.md (WRONG)
# Aniyomi Fork
My personal fork.
```

**Code (Bad):**
```kotlin
// WRONG - Don't rename classes
class RayniyomiRepository {  // Should be AniyomiRepository
}
```

---

## Checklist for New Features

When adding a new feature, ask:

1. **Will users see this text?**
   - Yes → Use "Rayniyomi"
   - No → Use "Aniyomi"

2. **Is this a code identifier?** (package, class, variable)
   - Yes → Use "Aniyomi"

3. **Is this referring to upstream?**
   - Yes → Use "Aniyomi" + attribution

4. **Is this internal documentation?**
   - Developer docs → Use "Aniyomi"
   - User docs → Use "Rayniyomi"

---

## Rationale

### Why This Matters

1. **Brand Consistency:** Users should see one name consistently
2. **Code Compatibility:** Keeps upstream merge conflicts minimal
3. **Attribution:** Clearly distinguishes our fork from upstream
4. **Developer Clarity:** Reduces confusion in code/docs

### Why Not Rename Everything?

**Package names:** Breaking change, kills compatibility with existing installations
**Class names:** Massive refactor, breaks existing plugins/extensions
**File paths:** Build system complexity, upstream merge conflicts

**Trade-off:** Live with internal "Aniyomi" to maintain compatibility, use "Rayniyomi" externally for brand identity.

---

## Frequently Asked Questions

### "Should I rename this variable from `aniyomi` to `rayniyomi`?"

**No.** Internal code uses "Aniyomi" for consistency with upstream.

### "What about comments that say 'Aniyomi'?"

**Leave them.** Unless they're user-facing or misleading.

### "The app says 'Aniyomi' in the title bar!"

**That's a bug.** File an issue to fix user-facing text.

### "Can I use 'Rayniyomi' in commit messages?"

**Yes,** for user-facing changes (feat/fix). Use "Aniyomi" for internal refactors.

---

## Enforcement

- **Code Reviews:** Check PR titles/descriptions for consistency
- **CI Checks:** (Future) Lint user-facing strings for "Aniyomi"
- **Issue Templates:** Remind contributors of naming rules
- **Monthly Audits:** Search for violations in docs/UI

---

## Related Policies

- [Branch Protection](branch-protection.md) - Enforces review before merge
- [Release Notes Policy](release-notes-policy.md) - Uses "Rayniyomi" in releases
- [Dependency Policy](dependency-policy.md) - References "Aniyomi" upstream

---

## Changelog

### v1.0.0 (2026-02-06)
- Initial naming conventions document
- Established Rayniyomi (user-facing) vs Aniyomi (internal) rules
- Added migration strategy and edge case examples
