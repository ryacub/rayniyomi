# Failure Analysis & Learning Policy

To prevent recurring incidents and improve fork stability, all significant failures (CI breaks, broken releases, misconfigurations) must undergo a structured failure analysis.

## Objectives
- Identify the **root cause** of the failure.
- Determine which **guardrail** was missing or failed.
- Create **prevention tickets** to ensure the same class of failure does not repeat.

## Criteria for Analysis
A failure analysis is mandatory for:
- Any `P0` or `P1` production bug.
- Any CI failure that blocks development for more than 4 hours.
- Any security or compliance violation.
- Any failed rollback drill.

## Process
1. **Declare**: Identify the failure and open a "Failure Analysis" ticket.
2. **Analyze**: Use the "5 Whys" method to find the root cause.
3. **Guardrail Audit**: Check why existing CI, lint, or manual review didn't catch it.
4. **Remediate**: Create follow-up remediation tickets (Lane C) for missing guardrails.
5. **Close**: Document the learnings and close the ticket once prevention work is tracked.

## Failure Analysis Template
All analyses must follow the [Failure Analysis Issue Template](../../.github/ISSUE_TEMPLATE/failure_analysis.yml).
