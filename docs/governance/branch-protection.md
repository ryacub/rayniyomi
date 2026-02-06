# Branch Protection Runbook

These settings enforce code quality and prevent accidental changes to protected branches.

## Core Protection Settings

**Apply to:** Default branch (typically `main` or `master`)

### Merge Requirements
- ✅ Require pull request before merging
- ✅ Require at least 1 approval review
- ✅ Require conversation resolution before merge
- ✅ Require branches to be up to date before merge

### Force Push & Deletion
- ✅ Restrict force pushes (prevents history rewriting)
- ✅ Restrict deletion of protected branch

### Code Ownership
- ✅ Require reviews from code owners (if CODEOWNERS file exists)

## Required Status Checks

**Project-specific checks (configure based on your CI/CD):**

### This Repository
- `Build app` - Main build verification (CI workflow)
- `Validate PR policy` - PR governance compliance (`pr_governance.yml`)
- `Gitleaks` - Secret scanning (`secret_scan.yml`)

### Common Status Checks (Examples)
- Build/compilation checks
- Unit test suites
- Linting/formatting checks
- Security scanning (secrets, dependencies, vulnerabilities)
- License compliance
- Code coverage thresholds

## Setup Instructions

### GitHub Web UI
1. Navigate to **Settings** → **Branches**
2. Click **Add rule** (or edit existing rule)
3. Enter branch name pattern (e.g., `main`)
4. Enable required settings:
   - ✅ Require pull request reviews before merging
     - Required approvals: `1` (adjust as needed)
   - ✅ Require status checks to pass before merging
     - Search and select status checks from dropdown
     - ✅ Require branches to be up to date
   - ✅ Require conversation resolution before merging
   - ✅ Restrict force pushes
   - ✅ Restrict deletions
5. Click **Create** or **Save changes**

### GitHub CLI
```bash
# View current protection rules
gh api repos/{owner}/{repo}/branches/main/protection

# Example: Enable basic protection
gh api repos/{owner}/{repo}/branches/main/protection \
  --method PUT \
  --field required_pull_request_reviews='{"required_approving_review_count":1}' \
  --field enforce_admins=true \
  --field restrictions=null
```

## Verification Checklist

After setup, verify:
- [ ] Test PR cannot merge without approval
- [ ] Test PR cannot merge with failing status checks
- [ ] Test PR cannot merge with unresolved conversations
- [ ] Force push to protected branch is blocked
- [ ] Branch deletion is blocked

## Troubleshooting

### Issue: Required checks not appearing
**Solution:** Checks only appear after running at least once on a PR branch. Create a test PR to populate the list.

### Issue: PR blocked by missing check
**Solution:** Check for typos in required check names. Check names are case-sensitive and must match CI job names exactly.

### Issue: Checks passing but merge still blocked
**Solution:** Branch may be out of date. Rebase/merge main into PR branch and re-run checks.

## Operational Best Practices

1. **Treat required check names as stable contracts**
   - Renaming CI jobs breaks branch protection
   - If renaming, update branch protection in the same PR

2. **Use descriptive, stable job names**
   - ✅ Good: `Build app`, `Run tests`, `Security scan`
   - ❌ Bad: `CI`, `Check`, `Job 1`

3. **Fork-safe CI checks**
   - Some GitHub features (dependency review, code scanning) unavailable on forks
   - Gate these checks conditionally: `if: github.event.pull_request.head.repo.full_name == github.repository`

4. **Document required contexts**
   - List required status check names in project docs
   - Update docs when checks change

## Recovery Procedures

### Temporarily disable protection (emergency only)
1. Document reason and expected duration
2. Navigate to branch protection rules
3. Click **Edit** → Uncheck requirements → **Save**
4. Perform emergency fix
5. **Re-enable protection immediately after fix**
6. Document what was changed and why protection was bypassed

### Update required status checks
```bash
# 1. Identify current required contexts
gh api repos/{owner}/{repo}/branches/main/protection/required_status_checks

# 2. Update contexts (replace with your check names)
gh api repos/{owner}/{repo}/branches/main/protection/required_status_checks \
  --method PATCH \
  --field contexts[]='Build app' \
  --field contexts[]='Gitleaks' \
  --field contexts[]='Validate PR policy'
```

---

**Last verified:** 2026-02-06 (smoke ticket execution validated)
