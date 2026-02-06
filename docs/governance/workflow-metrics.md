# Weekly Workflow Retro Metrics (R48)

To ensure the health of the `rayniyomi` fork's development process, we track the following metrics weekly.

## 1. Velocity & Throughput
*   **Tickets Closed:** Number of tickets moved to `Done` per week.
*   **Cycle Time:** Average time from `In Progress` to `Done`.

## 2. Quality Assurance
*   **CI Pass Rate:** Percentage of PRs that pass CI on the first run.
*   **Rejection Rate:** Percentage of PRs returned to `In Progress` after review.
*   **Regression Rate:** Number of bugs found in `main` that were introduced in the last sprint.

## 3. Governance Compliance
*   **Docs Freshness:** Are ADRs and Governance docs updated with code changes?
*   **Ticket Discipline:** Percentage of PRs with properly linked tickets and full metadata.

## Process
1.  **Review:** Conducted weekly by the Lead Agent/Maintainer.
2.  **Report:** Posted to the Remediation Board or Sprint Issue.
3.  **Adapt:** Low metrics trigger a process review (e.g., if CI Pass Rate < 80%, investigate flaky tests or local setup).
