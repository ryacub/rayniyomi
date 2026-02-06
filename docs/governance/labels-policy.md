# Label Policy

## Required labels by scenario
- High-risk (`P0` or `T3`): `breaking-change`, `rollback-tested`
- User-visible behavior changes: `user-facing`
- Dependency upgrades: `Dependencies`
- Operational readiness drills: `Operations`

## Usage notes
- Add labels early in PR lifecycle so policy checks can evaluate correctly.
- Do not merge high-risk PRs without both required high-risk labels.
- If a PR starts low-risk and becomes high-risk, update labels immediately.
