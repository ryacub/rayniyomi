# Orchestration Plan Review: Claude Code Agent Teams for Rayniyomi

**Reviewed by:** Claude Opus 4.6
**Date:** 2026-02-07
**Context:** Multi-agent orchestration strategy for 50+ ticket remediation sprint

---

## Executive Summary

The proposed plan to parallelize lane execution using Claude Code Agent Teams addresses a real bottleneck -- serial ticket execution in a 50+ ticket remediation sprint. However, the plan underestimates the fragility of git workflow coordination among concurrent agents, overestimates lane independence, and does not adequately account for the high-conflict file problem that already plagues this codebase. Below is a thorough analysis with actionable recommendations.

---

## 1. Strengths

### 1.1 Lane-Based Parallelism is Well-Structured

The R-BOARD already organizes tickets into five lanes (A: UI/Lifecycle, B: Downloads, C: Architecture, D: TODOs, E: Tests). These lanes have reasonably low cross-lane coupling for tickets within the same phase. Assigning one agent per lane is a natural decomposition.

### 1.2 Risk-Tiered Approval is Sound

Using plan-based approval gates for T3 (high-risk) tickets -- concurrency/threading, storage migration, startup flow changes -- is the right call. The existing verification matrix (T1: lint+targeted, T2: module tests+manual, T3: broad suite+regression+rollback) provides clear escalation criteria.

### 1.3 TaskList/R-BOARD Sync Addresses Visibility

Keeping the Claude Code TaskList synchronized with the GitHub R-BOARD (issue #71) gives both agent and human participants a single source of truth. This is critical for the "daily board hygiene sweep" the playbook already mandates.

### 1.4 Delegate-Mode Lead is a Good Separation of Concerns

Keeping the lead agent out of code and focused on coordination reduces the risk of the lead accidentally introducing scope creep or conflicting changes.

---

## 2. Risks and Gaps

### 2.1 CRITICAL: Git Branch Pollution Will Get Worse, Not Better

**This is the single biggest risk in the entire plan.**

The project's documented biggest pain point (MEMORY.md, CLAUDE.md, LLM_DELIVERY_PLAYBOOK.md) is PR pollution from branches created off dirty HEAD. PR #103 (R04) was polluted with 48 commits from R01, R02, and R18 because the branch was created from a merge commit instead of clean main.

**With 3-5 concurrent agents, this problem scales quadratically.** Each agent merging to main changes the target for every other agent. If agent-B finishes R10 and merges while agent-A is mid-work on R03, agent-A must rebase before its PR is clean. With 5 agents, the rebase churn becomes continuous.

**Specific failure modes:**
- Agent creates branch from local `main` that is behind `ryacub/main` by 3-4 merges from other agents
- Two agents modify the same high-conflict file (e.g., `PlayerViewModel.kt` at 2059 lines, touched by R20, R21, R23 -- all Lane A)
- Agent pushes to `ryacub` remote while another agent's PR is mid-merge, causing race conditions in GitHub's merge queue

### 2.2 High-Conflict Files Create Serialization Points Within Lanes

The AGENTS.md documents four high-conflict files requiring single-owner coordination:

| File | Lines | Tickets That Touch It |
|------|-------|-----------------------|
| `PlayerViewModel.kt` | 2,059 | R20, R21, R23 |
| `ReaderViewModel.kt` | 996 | R03, R22, R24 |
| `AnimeScreenModel.kt` | ~1,500+ | R05, R09 |
| `MangaScreenModel.kt` | ~1,500+ | R04, R09 |

Phase 4 tickets (R20-R24) are **all Lane A** and **all touch these high-conflict files**. Running them in parallel, even within the same lane agent, will produce unresolvable merge conflicts. The plan does not address this.

### 2.3 Cross-Lane Dependencies Are Not Truly Independent

Examining the R-BOARD dependency structure:

- **R08** (Lane C) is a migration prerequisite that gates R09 (Lane A), R10 (Lane B), R11 (Lane A)
- **R14** (Lane B) depends on R04 + R05 (both Lane B, already done)
- **R12** (Lane C) is a policy doc that gates no code but informs R09-R11 design
- Phase 4 tickets (R20-R24) all depend on Phase 2-3 completion
- **R30** (Lane E) provides test harness needed by R28, R29

The plan assumes lanes can run independently, but phases create hard serialization barriers.

### 2.4 R-BOARD Update Race Condition

The R-BOARD is a single GitHub issue (#71) with a markdown checklist body. If two agents finish tickets simultaneously and both try to update the R-BOARD via `gh issue edit 71 --body-file`, one will overwrite the other's changes. GitHub issue body edits are not atomic -- there is no CAS (compare-and-swap) mechanism.

### 2.5 Cost/Token Budget is Unaddressed

Running 5 concurrent Claude Code agents means 5x the token consumption. Each agent needs to:
- Read the full playbook, CLAUDE.md, AGENTS.md on startup
- Maintain conversation context for multi-step ticket execution
- Run Gradle builds (`./gradlew :app:compileDebugKotlin` takes significant time)
- Run the `android-code-review-debate` skill (spawns two more sub-agents)

For a single T2 ticket, the current workflow involves ~50-100k tokens. With 5 agents running review debates, that is 250-500k tokens per concurrent batch. Over 30 remaining tickets, this could exceed 5M tokens.

### 2.6 Build Contention

Android Gradle builds use significant disk I/O and memory. Running `./gradlew :app:assembleDebug` from 5 concurrent agents in the same working directory will cause:
- Gradle daemon port conflicts
- Build cache corruption
- Non-deterministic build failures that agents will misinterpret as code errors

### 2.7 No Rollback Strategy for Orchestration Failures

The plan describes how to parallelize but not how to recover when orchestration fails.

---

## 3. Concrete Improvements

### 3.1 Implement a Git Coordination Protocol

**Replace ad-hoc branch creation with a centralized branching lock.**

```bash
# Lead agent maintains a BRANCH_LOCK state:
# Before any agent creates a branch:
1. Agent requests branch creation from lead
2. Lead runs: git fetch ryacub && git rev-parse ryacub/main
3. Lead provides the exact commit SHA to branch from
4. Agent creates branch: git checkout -b claude/r##-slug <SHA>
5. Lead records branch ownership in TaskList metadata

# Before any agent pushes/merges:
1. Agent notifies lead: "Ready to push R## branch"
2. Lead checks no other merge is in flight
3. Lead approves push
4. Agent pushes, creates PR
5. Lead merges (single merge authority)
6. Lead updates R-BOARD
7. Lead broadcasts: "main updated to <new-SHA>, all agents rebase"
```

**Why this works:** It serializes the mutation of `main` through a single coordinator while allowing parallel development. The lead's delegate-mode role is already positioned for this.

### 3.2 Phase-Gated Parallelism, Not Lane Parallelism

Instead of 5 persistent lane agents, use phase-gated batches:

```
Phase 1 (remaining): R03, R06, R07 -- 3 agents, no file overlap
  Wait for all to merge.

Phase 2: R08 first (gate), then R09+R10+R11+R12+R13 -- 5 agents
  R08 merges first, then 5 parallel.

Phase 3: R14+R15+R17+R18+R19 -- 5 agents, check file overlap
  R17 and R18 may conflict (both consolidate screens).

Phase 4: SERIAL within Lane A -- R20, R21, R22, R23, R24
  These all touch PlayerViewModel/ReaderViewModel.
  Maximum 2 agents: one for Player (R20,R21,R23), one for Reader (R22,R24).

Phase 5: R25+R26+R27+R28+R29+R30 -- 6 agents, all independent
  Safest phase for full parallelism.
```

**Why this works:** It respects the actual dependency graph rather than assuming lane independence. Phase 4 gets the file-ownership serialization it needs. Phase 5, being all TODOs and tests, gets maximum parallelism.

### 3.3 Isolated Working Directories

Each agent should work in its own clone of the repository:

```bash
# Lead creates isolated workspaces:
git clone git@github.com:ryacub/rayniyomi.git /tmp/rayniyomi-agent-a
git clone git@github.com:ryacub/rayniyomi.git /tmp/rayniyomi-agent-b
# etc.
```

This eliminates:
- Gradle daemon conflicts
- Build cache corruption
- Working tree conflicts between concurrent `git checkout` operations
- Accidental `git stash` collisions

**Cost:** ~500MB per clone. For 5 agents, ~2.5GB. Acceptable on modern machines.

### 3.4 Atomic R-BOARD Updates via Lead Only

**Only the lead agent should update the R-BOARD.** Worker agents report task completion via `TaskUpdate`. The lead then:

1. Reads current R-BOARD body
2. Updates the checkbox
3. Writes back

This serializes R-BOARD mutations and prevents the race condition.

### 3.5 Add a Pre-Merge Conflict Check

Before any agent's PR is merged, the lead should verify:

```bash
# Check if the PR branch is clean against current main:
git fetch ryacub
git merge-tree $(git merge-base ryacub/main agent-branch) ryacub/main agent-branch
# If conflicts detected, agent must rebase first
```

This catches conflicts before they hit GitHub's merge machinery.

### 3.6 Budget-Aware Agent Spawning

Instead of spawning all agents upfront, use a pool model:

```
MAX_CONCURRENT_AGENTS = 3  # Start conservative
TOKEN_BUDGET_PER_TICKET = 150k  # Based on observed T2 ticket cost
TOTAL_BUDGET = 3M tokens

# Lead tracks:
- tokens_used: cumulative
- agents_active: count
- tickets_in_flight: list

# Scale up only if:
- All active agents are idle-waiting (blocked on each other)
- Budget remaining > 3 * TOKEN_BUDGET_PER_TICKET
- No merge conflicts pending resolution
```

### 3.7 Skip Code Review Debate for T1 Tickets

The `android-code-review-debate` skill spawns two sub-agents per ticket. For T1 (low-risk) tickets like R25 ("Fix `InMemoryPreferenceStore.getStringSet` TODO"), the review debate is overkill. Reserve it for T2 and T3 only.

**Savings estimate:** T1 tickets are ~40% of remaining work. At ~50k tokens per debate, this saves ~600k tokens.

---

## 4. Alternative Approaches

### 4.1 Pipeline Model Instead of Team Model

Instead of parallel lanes, use a producer-consumer pipeline:

```
Stage 1 (Planner): Reads ticket, produces implementation plan
Stage 2 (Implementer): Executes plan task-by-task, produces branch
Stage 3 (Reviewer): Runs android-code-review-debate, produces review report
Stage 4 (Merger): Lead merges, updates R-BOARD, triggers rebases

Multiple tickets flow through the pipeline concurrently at different stages.
```

**Advantage:** Natural backpressure. If Stage 4 (merging) is slow, Stage 2 agents naturally block, preventing branch pile-up.

**Disadvantage:** Higher latency per ticket. Better for sustained throughput than burst parallelism.

### 4.2 Pair-Programming Model

Instead of independent agents, pair agents on related tickets:

```
Pair 1: R03 + R06 (both Lane A, Phase 1, both touch UI lifecycle)
Pair 2: R09 + R11 (both Lane A, Phase 2, both migrate off global launch)
Pair 3: R20 + R21 (both touch PlayerViewModel -- must coordinate)
```

One agent implements, the other reviews in real-time. This catches issues faster than post-hoc review and ensures file ownership is coordinated.

### 4.3 Human-in-the-Loop Merge Gates

For T2/T3 tickets, require human approval before merge rather than agent-only review. The lead agent prepares the PR with full evidence, then pauses for human sign-off. This adds latency but dramatically reduces the risk of regressions in production code.

---

## 5. Rollout Strategy

### Phase A: Single-Agent Validation (1 day)

**Goal:** Validate the orchestration tooling works at all.

1. Lead agent + one worker agent
2. Worker executes R03 (T1, Lane A, 0.5d, no file conflicts)
3. Lead coordinates branching, merge, R-BOARD update
4. Measure: time-to-merge, token cost, git hygiene quality
5. **Gate:** Proceed only if the branch was clean, PR was scoped, and no manual intervention was needed

### Phase B: Two-Agent Parallelism (2 days)

**Goal:** Validate that two agents can work concurrently without git conflicts.

1. Lead + two worker agents
2. Worker-A executes R06 (Lane A), Worker-B executes R07 (Lane E)
3. These are in different lanes with zero file overlap
4. Lead manages merge ordering
5. **Gate:** Both PRs merge cleanly without rebase issues

### Phase C: Phase-2 Batch (3-4 days)

**Goal:** First real parallel execution with dependencies.

1. Lead + three worker agents
2. Worker-C executes R08 (Lane C, gate ticket)
3. After R08 merges, Worker-A executes R09 (Lane A), Worker-B executes R10 (Lane B)
4. Lead broadcasts main SHA after each merge
5. **Gate:** All three merge cleanly, no cross-contamination

### Phase D: Full Parallel (Phase 5)

**Goal:** Maximum parallelism on the safest batch.

1. Lead + up to 5 worker agents
2. Phase 5 tickets (R25-R30) are all independent TODOs and tests
3. This is the safest batch for full parallelism
4. **Gate:** Measure actual throughput improvement vs. single-agent baseline

### Phase E: High-Risk Parallel (Phase 4)

**Goal:** Tackle the hardest batch with file-ownership coordination.

1. Lead + 2 worker agents (not 5)
2. Worker-Player handles R20, R21, R23 (PlayerViewModel chain)
3. Worker-Reader handles R22, R24 (ReaderViewModel chain)
4. Strict serialization within each worker
5. T3 tickets require plan-mode approval

---

## 6. Success Metrics

### 6.1 Primary Metrics

| Metric | Baseline (Single Agent) | Target (Orchestrated) | Measurement |
|--------|------------------------|-----------------------|-------------|
| **Tickets merged per day** | 1-2 | 3-5 | Count from git log |
| **Time to merge (T1)** | 2-4 hours | 1-2 hours | PR create to merge timestamp |
| **Time to merge (T2)** | 4-8 hours | 3-6 hours | PR create to merge timestamp |
| **Branch pollution rate** | ~20% (1 in 5 PRs) | 0% | Count of PRs requiring cherry-pick cleanup |
| **Merge conflict rate** | Unknown | <10% of PRs | Count of PRs requiring rebase after push |

### 6.2 Quality Metrics

| Metric | Acceptable | Red Flag | Measurement |
|--------|-----------|----------|-------------|
| **Scope violations** | 0 per batch | Any PR with out-of-scope changes | Code review |
| **Regression rate** | 0 | Any test failure introduced by merge | CI results |
| **R-BOARD accuracy** | 100% synced | Any ticket marked done that is not merged | Manual audit |
| **Token cost per ticket** | <200k (T1), <300k (T2) | >500k for any ticket | Token tracking |

### 6.3 Process Health Metrics

| Metric | Healthy | Unhealthy |
|--------|---------|-----------|
| **Agent idle time** | <20% of session | >50% (blocked on coordination) |
| **Lead coordination overhead** | <30% of total tokens | >50% (lead becomes bottleneck) |
| **Rebase frequency** | <1 per ticket | >3 per ticket (churn) |
| **Human intervention rate** | 0 for T1, <1 per batch for T2 | Any emergency intervention |

### 6.4 When to Abort

Stop the orchestration experiment and revert to single-agent if:

1. **Two consecutive PRs** are polluted with cross-ticket commits
2. **Any merge** introduces a regression that reaches main
3. **Token cost** exceeds 2x single-agent cost per ticket without proportional throughput gain
4. **Lead agent context** fills up and loses track of agent states

---

## 7. Summary of Recommendations

| Priority | Recommendation | Risk Mitigated |
|----------|---------------|----------------|
| **P0** | Centralize all `main` mutations through lead agent | Branch pollution (their #1 pain point) |
| **P0** | Isolated working directories per agent | Build contention, working tree conflicts |
| **P0** | Phase-gated batches, not persistent lane agents | Cross-phase dependencies, file conflicts |
| **P1** | Lead-only R-BOARD updates | Race condition on issue #71 |
| **P1** | Pre-merge conflict check | Silent merge conflicts |
| **P1** | Start with 2 agents, scale to 3, max 5 | Cost control, complexity management |
| **P2** | Skip code review debate for T1 | Token budget conservation |
| **P2** | Pair-programming for Phase 4 high-conflict files | PlayerViewModel/ReaderViewModel conflicts |
| **P2** | Human merge gate for T3 tickets | Production regression risk |

---

## Realistic Expectations

The 3-5x throughput claim is achievable for independent ticket batches (Phase 5) but unrealistic for dependency-heavy phases (Phase 2, 4). A more honest expectation is **1.5-2x sustained improvement** across the full sprint, with peaks of 3-4x on independent batches and near-1x on serialized batches.

**The highest-impact single change is centralizing merge authority in the lead agent.** This eliminates the branch pollution problem that has already cost the project significant rework time (PR #103) and will only get worse with concurrency.

---

## Next Steps

1. **Implement git coordination protocol** (Section 3.1) in lead agent logic
2. **Create isolated workspace setup script** for agent clones
3. **Update CLAUDE.md** with orchestration rules and phase-gating strategy
4. **Start Phase A validation** with R03 as proof-of-concept
5. **Measure baseline metrics** before scaling to Phase B
