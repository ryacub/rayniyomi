# TDD Policy

## Objective
Use test-driven development to reduce regressions and improve change confidence.

## Default Rule
- For behavior changes, follow Red -> Green -> Refactor.

## Required PR Evidence
- PR descriptions must include a `TDD Evidence` section covering:
  - Which failing test was added/updated first.
  - Which implementation change made it pass.
  - Any follow-up refactor done while tests stayed green.

## Allowed Exceptions
- Docs-only changes.
- Pure CI/workflow metadata changes.
- Non-behavior refactors with no runtime effect.

For exceptions, state explicit reason in `TDD Evidence` (for example: `Not applicable (docs-only change)`).

## Review Guidance
- Reviewers should verify tests actually fail without the implementation change.
- Reviewers should reject PRs that claim TDD but provide no concrete test evidence.
