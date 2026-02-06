# Branch Protection Runbook

These settings are required on the default branch.

## Required Branch Protection Settings
- Require pull request before merging.
- Require at least 1 approval review.
- Require conversation resolution before merge.
- Require branches to be up to date before merge.
- Require status checks to pass:
  - `Build app` (existing CI)
  - `Validate PR policy` (`pr_governance.yml`)
  - `Gitleaks` (`secret_scan.yml`)
- Restrict force pushes.
- Restrict deletion of protected branch.

## Admin Checklist
1. Open repository settings -> Branches.
2. Add/update protection rule for default branch.
3. Enable all required checks listed above.
4. Confirm CODEOWNERS is enforced via required reviews from code owners.
