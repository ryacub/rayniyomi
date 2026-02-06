# Remediation Sprint Board (Lane C)

This board tracks governance and technical debt remediation tasks for the `rayniyomi` fork.

## Active Sprint (Phase 6)

| ID | Priority | Task | Status | Branch / PR |
|---|---|---|---|---|
| R41 | P1 | Enforce LLM ticket workflow (CI + Docs sync) | In Progress | `r41/enforce-workflow` / PR #87 |
| R42 | P1 | Adopt Definition of Ready/Done checklists | Review | PR #83 |
| R39 | P1 | Add fork checklist to docs/PR template | Review | PR #84 |
| R43 | P1 | Add risk-tier model (T1/T2/T3) | Review | PR #85 |
| R44 | P1 | Enforce WIP limits and branch ownership | Done | Merged (#86) |
| R45 | P1 | Enforce rebase-before-merge guardrails | Review | PR #88 |
| R46 | P1 | Require rollback notes for P0/T3 tickets | Review | PR #89 |
| R47 | P2 | Failure-learning loop (post-mortems) | Review | PR #90 |
| R48 | P2 | Weekly workflow retro metrics | Review | PR #91 |
| R63 | P1 | Rewrite README for fork identity | Review | PR #92 |
| R62 | P3 | Audit and remove stale upstream app-name remnants | In Progress | `r62/remove-app-remnants` |

## Backlog (Lane C)

| ID | Priority | Task | Status |
|---|---|---|---|

## Governance Rules
1. **One ticket per branch.**
2. **Conventional Commits required:** `type: summary (R###)`.
3. **Verification matrix required** in all PRs.
4. **No WIP merging.** All PRs must pass CI.

---
*Last Updated: 2026-02-06*
