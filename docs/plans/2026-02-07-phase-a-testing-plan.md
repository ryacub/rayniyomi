# Phase A Testing & Data Collection Plan

**Goal:** Validate adaptive orchestration with multi-ticket batches (where it makes sense).

**Key Insight:** Orchestration overhead only pays off with 2+ tickets. Single-ticket testing doesn't show real value.

---

## Testing Strategy: Multi-Ticket Batches

### Break-Even Analysis

```
Single ticket (no orchestration):
- Direct implementation: 25k tokens
- Simple review: 5k tokens
- Total: 30k tokens

Single ticket (with orchestration):
- Planning overhead: 10k
- Worker spawn: 2k
- Execution: 12k
- Monitoring: 5k
- Review: 5k
- Total: 34k tokens
- Overhead: 4k tokens (13% waste)

Two tickets (with orchestration):
- Shared planning: 12k (6k per ticket)
- 2× execution: 24k
- 2× monitoring: 10k
- 2× review: 10k
- Total: 56k (28k per ticket)
- vs Single-agent: 60k (30k × 2)
- Savings: 4k (7% better)

Three tickets (with orchestration):
- Shared planning: 15k (5k per ticket)
- 3× execution: 36k
- 3× monitoring: 15k
- 3× review: 15k
- Total: 81k (27k per ticket)
- vs Single-agent: 90k (30k × 3)
- Savings: 9k (10% better)
```

**Conclusion:** Use orchestration for 2+ tickets minimum.

---

## Revised Phase A: Multi-Ticket Validation

### Phase A1: Two-Ticket Conservative Baseline (R03 + R06)

### Rationale

**Why 2 tickets:** Minimum to show batching value vs single-agent approach.

**Expected outcome:**
- Shared planning: 12k (vs 20k for 2 separate sessions)
- Per-ticket cost: ~28k (vs 30k single-agent)
- Total: ~56k (vs 60k unbatched)
- Validates orchestration makes sense

### Setup

```bash
# 1. Create data collection directory
mkdir -p docs/orchestration-data/phase-a1-r03-r06

# 2. Prepare workspaces
.local/setup-agent-workspace.sh worker-a
.local/setup-agent-workspace.sh worker-b  # Optional: for parallel work

# 3. Document baseline
cat > docs/orchestration-data/phase-a1-r03-r06/metadata.yaml <<EOF
phase: A1_conservative
tickets: [R03, R06]
date: $(date -u +"%Y-%m-%dT%H:%M:%SZ")
strategy: Sonnet planning + Sonnet execution (batched)
expected_tokens: 56000 (28k per ticket)
expected_savings: 7% vs single-agent (60k)
baseline_comparison: 242000 (V2: 2 × 121k with debates)
batching_value: Shared planning saves 8k overhead
EOF
```

### Execution

```yaml
Approach:
  - Tickets: R03 + R06 (batched)
  - Planning model: Sonnet (shared for both)
  - Execution model: Sonnet (both tickets)
  - Monitoring model: Haiku
  - Review tier: T1/T2 based on complexity

Steps:
  1. Invoke orchestration-lead skill with phase="A1_conservative"
  2. Shared planning analyzes both tickets
  3. Spawn worker(s) to handle both tickets
  4. Monitor for batching efficiency
  5. Track token usage per ticket and shared costs
```

### Batching Strategy

**Option A: Sequential (safer):**
```
Worker-A handles R03 → completes → handles R06
- Simpler coordination
- Can't parallelize
- Total time: ~20 minutes
```

**Option B: Parallel (efficient):**
```
Worker-A handles R03
Worker-B handles R06 (simultaneously)
- Faster completion
- Tests coordination
- Total time: ~12 minutes
```

**Recommendation:** Start with Option A (sequential) for Phase A1 to establish baseline.

### Data Collection

**Manual tracking during execution:**

```yaml
# docs/orchestration-data/phase-a1-r03-r06/execution-log.yaml

shared_planning:
  model: sonnet
  tickets: [R03, R06]
  start_time: "2026-02-07T10:00:00Z"
  end_time: "2026-02-07T10:03:00Z"
  estimated_tokens: 12000  # SHARED COST (6k per ticket)
  activities:
    - Analyze both tickets
    - Classify complexity (R03: mechanical, R06: nuanced)
    - Create tasks for both
    - Define review criteria

tickets:
  R03:
    branch_creation:
      model: sonnet
      worker: worker-a
      task_id: "1"
      estimated_tokens: 2000
      status: completed

    implementation:
      model: sonnet
      worker: worker-a
      task_id: "2"
      estimated_tokens: 12000
      status: completed
      fix_loops: 0

    monitoring:
      model: haiku
      estimated_tokens: 5000
      activities: [git_verify, conflict_check, t1_checklist]

    review:
      tier: T1
      type: checklist
      estimated_tokens: 5000
      result: PASS

    finalization:
      model: haiku
      estimated_tokens: 8000

    subtotal: 32000  # Per-ticket cost

  R06:
    branch_creation:
      model: sonnet
      worker: worker-a
      task_id: "3"
      estimated_tokens: 2000
      status: completed

    implementation:
      model: sonnet
      worker: worker-a
      task_id: "4"
      estimated_tokens: 12000
      status: completed
      fix_loops: 0

    monitoring:
      model: haiku
      estimated_tokens: 5000

    review:
      tier: T2
      type: android-expert
      estimated_tokens: 15000  # More complex than R03
      result: PASS

    finalization:
      model: haiku
      estimated_tokens: 8000

    subtotal: 42000  # Per-ticket cost (T2 review more expensive)

  branch_creation:
    model: sonnet
    worker: worker-a
    task_id: "1"
    start_time: "2026-02-07T10:03:00Z"
    end_time: "2026-02-07T10:03:30Z"
    estimated_tokens: 2000
    status: completed
    verification: passed

  implementation:
    model: sonnet
    worker: worker-a
    task_id: "2"
    start_time: "2026-02-07T10:04:00Z"
    end_time: "2026-02-07T10:08:00Z"
    estimated_tokens: 12000
    status: completed
    verification: passed
    fix_loops: 0

  monitoring:
    model: haiku
    start_time: "2026-02-07T10:08:30Z"
    end_time: "2026-02-07T10:09:00Z"
    estimated_tokens: 5000
    activities:
      - Git log verification
      - Conflict check
      - T1 checklist execution

  review:
    tier: T1
    type: checklist
    estimated_tokens: 5000
    result: PASS
    issues_found: 0
    checklist_results:
      - "Code compiles": PASS
      - "Follows pattern": PASS
      - "No withUIContext": PASS
      - "Uses launchIO": PASS
      - "Commit message": PASS

  finalization:
    model: haiku
    start_time: "2026-02-07T10:10:00Z"
    end_time: "2026-02-07T10:11:00Z"
    estimated_tokens: 8000
    activities:
      - Push branch
      - Create PR
      - Merge PR
      - Update R-BOARD

totals:
  shared_planning: 12000  # Key batching optimization
  r03_cost: 32000
  r06_cost: 42000
  total_tokens: 86000  # 12k shared + 32k + 42k
  per_ticket_average: 43000  # But this includes shared cost!

batching_analysis:
  # Without batching (separate sessions):
  unbatched_cost: 96000  # (10k + 32k) + (10k + 42k)
  planning_overhead: 20000  # 2 × 10k separate planning

  # With batching:
  batched_cost: 86000  # 12k shared + 74k execution
  planning_overhead: 12000  # Shared planning
  batching_savings: 10000  # 20k - 12k = 8k planning savings

  # Per-ticket effective cost:
  effective_per_ticket: 43000  # (86k / 2)
  vs_single_agent: 30000  # Simple single-agent
  orchestration_worth_it: false  # For just 2 simple tickets

  # BUT vs V2 baseline (with debates):
  v2_baseline: 242000  # 2 × 121k
  savings_vs_v2: 64%  # Still massive savings

comparison_to_single_agent:
  single_agent_total: 60000  # 2 × 30k
  orchestration_total: 86000
  delta: -26000  # WORSE by 26k tokens!
  conclusion: "Orchestration overhead not worth it for 2 simple tickets vs single-agent"

comparison_to_v2_orchestration:
  v2_total: 242000  # 2 × 121k (with debates)
  a1_total: 86000
  savings: 156000  # 64% savings
  conclusion: "Massive savings vs V2, but V2 was over-engineered (debates on T1)"

key_insight:
  "Orchestration beats V2 (64% savings) but loses to single-agent for simple tickets.
   Need 3+ tickets OR complex tickets (T2/T3) to beat single-agent approach."
```

### Automated Data Extraction

**After execution, extract from session transcript:**

```bash
# Extract token usage from Claude Code session
# Location: ~/.claude/projects/-Users-rayyacub-Documents-rayniyomi/*.jsonl

# Create extraction script
cat > docs/orchestration-data/extract-tokens.py <<'EOF'
#!/usr/bin/env python3
"""Extract token usage from Claude Code session transcripts."""

import json
import sys
from pathlib import Path

def extract_session_data(session_file):
    """Parse session JSONL and extract token metrics."""
    data = {
        "agents_spawned": [],
        "total_tokens": 0,
        "tokens_by_agent": {},
        "tools_used": [],
        "task_completions": []
    }

    with open(session_file) as f:
        for line in f:
            msg = json.loads(line)

            # Extract agent spawns
            if msg.get("type") == "tool_use" and msg.get("name") == "Task":
                params = msg.get("parameters", {})
                data["agents_spawned"].append({
                    "name": params.get("name"),
                    "subagent_type": params.get("subagent_type"),
                    "model": params.get("model", "sonnet"),
                    "description": params.get("description")
                })

            # Extract token usage
            if "usage" in msg:
                usage = msg["usage"]
                data["total_tokens"] += usage.get("input_tokens", 0)
                data["total_tokens"] += usage.get("output_tokens", 0)

            # Extract task updates
            if msg.get("type") == "tool_use" and msg.get("name") == "TaskUpdate":
                params = msg.get("parameters", {})
                if params.get("status") == "completed":
                    data["task_completions"].append({
                        "task_id": params.get("taskId"),
                        "status": params.get("status")
                    })

    return data

if __name__ == "__main__":
    session_file = sys.argv[1] if len(sys.argv) > 1 else None
    if not session_file:
        print("Usage: extract-tokens.py <session-file.jsonl>")
        sys.exit(1)

    data = extract_session_data(session_file)
    print(json.dumps(data, indent=2))
EOF

chmod +x docs/orchestration-data/extract-tokens.py

# Run extraction
./docs/orchestration-data/extract-tokens.py \
  ~/.claude/projects/-Users-rayyacub-Documents-rayniyomi/<session-id>.jsonl \
  > docs/orchestration-data/phase-a1-r03/session-data.json
```

### Success Criteria

```yaml
Token Efficiency:
  - Target: < 40,000 tokens
  - Stretch: < 35,000 tokens
  - ✅ PASS if: total_tokens < 40000

Quality:
  - Target: Passes review within 2 attempts
  - ✅ PASS if: review_attempts <= 2 AND fix_loops <= 1

Time:
  - Target: < 30 minutes end-to-end
  - ✅ PASS if: total_time_minutes < 30

Comparison to Baseline:
  - V2 baseline: 121,000 tokens
  - Savings: ((121000 - actual) / 121000) * 100
  - ✅ PASS if: savings >= 60%
```

### Analysis

```bash
# Generate Phase A1 report
cat > docs/orchestration-data/phase-a1-r03/analysis.md <<EOF
# Phase A1 Analysis: R03 Conservative Baseline

## Results

**Token Usage:**
- Planning (Sonnet): ${planning_tokens}
- Execution (Sonnet): ${execution_tokens}
- Monitoring (Haiku): ${monitoring_tokens}
- Review (T1): ${review_tokens}
- Finalization (Haiku): ${finalization_tokens}
- **Total: ${total_tokens}**

**Quality:**
- Review attempts: ${review_attempts}
- Fix loops: ${fix_loops}
- Final status: ${final_status}

**Efficiency:**
- Time to completion: ${time_minutes} minutes
- Savings vs V2: ${savings_percent}%
- Cost per token (if Sonnet = $1): $${cost}

## Comparison to Baseline

| Metric | V2 Baseline | A1 Conservative | Improvement |
|--------|-------------|-----------------|-------------|
| Tokens | 121,000 | ${total_tokens} | ${savings_percent}% |
| Review cost | 66,000 (debate) | 5,000 (checklist) | 92% |
| Fix loops | 1 (avg) | ${fix_loops} | - |

## Insights

**What worked well:**
- Sonnet execution quality: [assessment]
- T1 checklist effectiveness: [assessment]
- Haiku monitoring: [assessment]

**Issues encountered:**
- [List any problems]

**Recommendations for A2:**
- Should we test Haiku execution? [YES/NO based on results]
- Risk tolerance: [LOW/MEDIUM/HIGH]

## Decision: Proceed to Phase A2?

✅ YES - Quality maintained, ready to test Haiku
❌ NO - Issues found, need to refine A1 approach

EOF
```

---

## Phase A2: Three-Ticket Batch with Haiku Testing (R03 + R06 + R07)

### Rationale

**Why 3 tickets:**
- Break-even point where orchestration beats single-agent
- Shared planning cost amortized across 3 tickets
- Can test Haiku on R03 (mechanical) while using Sonnet for R06/R07

**Expected outcome:**
- Shared planning: 15k (5k per ticket vs 10k separate)
- Per-ticket cost: ~27-30k (vs 30k single-agent)
- Total: ~85-90k (vs 90k unbatched single-agent)
- Validates orchestration efficiency at scale

### Setup

```bash
# 1. Create data collection directory
mkdir -p docs/orchestration-data/phase-a2-r03-r06-r07

# 2. Review A1 results
cat docs/orchestration-data/phase-a1-r03-r06/analysis.md

# 3. Prepare workspaces
.local/setup-agent-workspace.sh worker-a
.local/setup-agent-workspace.sh worker-b
.local/setup-agent-workspace.sh worker-c  # For parallel execution

# 4. Document experiment
cat > docs/orchestration-data/phase-a2-r03-r06-r07/metadata.yaml <<EOF
phase: A2_experimental
tickets: [R03, R06, R07]
date: $(date -u +"%Y-%m-%dT%H:%M:%SZ")
strategy: Shared planning + Adaptive execution (TEST Haiku on R03)
expected_tokens: 85000 (28k per ticket average)
hypothesis:
  - Haiku works for mechanical (R03)
  - Sonnet needed for nuanced (R06, R07)
  - Batching at 3 tickets breaks even with single-agent
risk: MEDIUM - Haiku on R03 experimental
comparison:
  - A1: 86k for 2 tickets (43k per ticket)
  - Single-agent: 90k for 3 tickets (30k per ticket)
  - V2 baseline: 363k for 3 tickets (121k per ticket)
EOF
```

### Execution

```yaml
Approach:
  - Tickets: R03 + R06 + R07 (batched)
  - Planning model: Sonnet (shared for all 3)
  - Execution models: ADAPTIVE
    - R03: Haiku (TEST - mechanical refactor)
    - R06: Sonnet (nuanced - lifecycle)
    - R07: Sonnet (nuanced - state management)
  - Monitoring model: Haiku (all)
  - Review tiers: T1/T2 based on complexity

Steps:
  1. Invoke orchestration-lead skill with phase="A2_experimental"
  2. Shared planning classifies all 3 tickets
  3. Spawn 3 workers (or 1 sequential) with model assignments
  4. Monitor Haiku (R03) vs Sonnet (R06/R07) quality
  5. Track batching efficiency at 3-ticket scale
  6. Compare to single-agent baseline (90k)
```

### Model Selection Per Ticket

```python
# Adaptive logic for Phase A2
R03: {
  complexity: "mechanical",
  execution: "haiku",     # TEST
  review: "T1",
  expected: 28k
}

R06: {
  complexity: "nuanced",
  execution: "sonnet",    # PROVEN
  review: "T2",
  expected: 42k
}

R07: {
  complexity: "nuanced",
  execution: "sonnet",    # PROVEN
  review: "T2",
  expected: 42k
}

# Total: 15k planning + 28k + 42k + 42k = 127k
# Wait, that's worse than A1!

# Ah, but if we skip debate on T1/T2:
R03: 28k (T1 checklist)
R06: 32k (T2 expert, not debate)
R07: 32k (T2 expert, not debate)
Total: 15k + 92k = 107k
Per-ticket: 35.7k

# vs Single-agent: 90k (3 × 30k)
# Still not quite there...

# BUT: If Haiku + T1 really work:
R03: 23k (8k haiku + 5k checklist + 5k monitoring + 5k finalization)
R06: 32k (12k sonnet + 5k monitoring + 10k expert + 5k finalization)
R07: 32k (same)
Total: 15k + 87k = 102k
Per-ticket: 34k

# Getting closer to single-agent parity!
```

### Data Collection

**Same structure as A1, with additional metrics:**

```yaml
# docs/orchestration-data/phase-a2-r06/execution-log.yaml

implementation:
  model: haiku  # KEY DIFFERENCE
  worker: worker-a
  task_id: "2"
  estimated_tokens: 8000  # vs 12000 Sonnet
  status: [completed | requires_fix]
  fix_loops: [0 | 1 | 2+]

  # NEW: Error analysis
  errors_encountered:
    - type: [mechanical | logic | architectural]
      description: "..."
      required_upgrade: [no | haiku_retry | sonnet | opus]

  # NEW: Quality comparison
  quality_vs_sonnet:
    code_correctness: [same | worse | better]
    pattern_following: [same | worse | better]
    edge_case_handling: [same | worse | better]

# NEW: Cost-effectiveness analysis
cost_analysis:
  base_cost: 28000  # Haiku success on first try
  with_1_fix: 40000  # +12k Sonnet fix
  with_2_fixes: 52000  # +24k fixes
  actual_cost: ${actual}
  effective_savings: ((121000 - actual) / 121000) * 100
```

### Success Criteria

```yaml
Token Efficiency:
  - Target: < 45,000 tokens (includes 1 fix loop)
  - Stretch: < 30,000 tokens (Haiku succeeds first try)
  - ✅ PASS if: actual_tokens < 45000

Quality:
  - Target: Same quality as A1 within 2 total attempts
  - ✅ PASS if: quality_vs_sonnet >= "same" AND attempts <= 2

Haiku Validation:
  - Target: Haiku success rate >= 70% (0-1 fix loops acceptable)
  - ✅ PASS if: fix_loops <= 1

Comparison to A1:
  - A1 cost: 32,000 tokens
  - A2 target: < 32,000 tokens (if Haiku works)
  - ✅ PASS if: effective_cost < A1_cost OR quality_maintained
```

### Analysis

```bash
# Generate Phase A2 report with comparison to A1
cat > docs/orchestration-data/phase-a2-r06/analysis.md <<EOF
# Phase A2 Analysis: R06 Haiku Validation

## Results

**Token Usage:**
- Planning (Sonnet): ${planning_tokens}
- Execution (Haiku): ${execution_tokens}
- Fix loops (if any): ${fix_tokens}
- Monitoring (Haiku): ${monitoring_tokens}
- Review: ${review_tokens}
- Finalization (Haiku): ${finalization_tokens}
- **Total: ${total_tokens}**
- **Effective cost: ${actual_with_fixes}**

**Quality Assessment:**
- Haiku first attempt: [PASS/FAIL]
- Fix loops required: ${fix_loops}
- Error types: [mechanical/logic/architectural]
- Quality vs Sonnet (A1): [same/worse/better]

## Comparison: A2 vs A1

| Metric | A1 (Sonnet) | A2 (Haiku) | Delta |
|--------|-------------|------------|-------|
| Base tokens | 32,000 | 28,000 | -4,000 (-12.5%) |
| Actual tokens | 32,000 | ${actual} | ${delta} |
| Fix loops | ${a1_fix_loops} | ${fix_loops} | ${delta} |
| Quality | ${a1_quality} | ${a2_quality} | ${comparison} |

## Haiku Performance Analysis

**Success rate:** ${success_rate}%
- First attempt success: [YES/NO]
- Required fixes: [0/1/2]
- Upgrade needed: [none/sonnet/opus]

**Error patterns:**
${error_analysis}

**Cost effectiveness:**
- Break-even point: 9 fix loops
- Actual fix loops: ${fix_loops}
- Still cost effective? [YES/NO]

## Decision: Adopt Haiku for Phase A3?

### If success_rate >= 70%:
✅ **ADOPT** - Use Haiku for T1 mechanical tasks in Phase A3
- Effective cost: ~28-30k per ticket
- Quality: Maintained with review gates
- Risk: Low with progressive upgrade

### If success_rate < 70%:
❌ **REJECT** - Stick with Sonnet for implementation
- Effective cost: ~32k per ticket (still 74% savings)
- Quality: Proven
- Risk: Low

## Phase A3 Strategy

Based on A1 and A2 data:

\`\`\`python
def select_execution_model(task_complexity):
    if task_complexity == "mechanical":
        if haiku_success_rate >= 70%:
            return "haiku"  # 8k, validated
        else:
            return "sonnet"  # 12k, proven
    elif task_complexity == "nuanced":
        return "sonnet"  # Always use for nuanced
    else:
        return "sonnet"  # Default safe choice
\`\`\`

EOF
```

---

## Phase A3: Data-Driven Execution (Phase 1)

### Setup

```bash
# 1. Review A1 and A2 results
cat docs/orchestration-data/phase-a1-r03/analysis.md
cat docs/orchestration-data/phase-a2-r06/analysis.md

# 2. Create Phase A3 strategy
cat > docs/orchestration-data/phase-a3-strategy.yaml <<EOF
phase: A3_adaptive
tickets: [R03, R06, R07]
date: $(date -u +"%Y-%m-%dT%H:%M:%SZ")

# Model selection rules (data-driven from A1/A2)
model_selection:
  planning:
    T1: sonnet  # 10k
    T2: sonnet  # 12k
    T3: opus    # 20k

  execution:
    mechanical:
      model: ${A2_DECISION}  # haiku if A2 passed, else sonnet
      rationale: "A2 success rate: ${A2_SUCCESS_RATE}%"
    nuanced:
      model: sonnet  # Always proven
      rationale: "Lifecycle/coroutines need judgment"

  monitoring: haiku  # Always (mechanical)

  review:
    T1: checklist  # 5k
    T2: android-expert  # 25k
    T3: debate  # 76k

# Expected costs per ticket
estimated_costs:
  R03: ${estimated_r03}  # Based on complexity + model
  R06: ${estimated_r06}
  R07: ${estimated_r07}
  total: ${estimated_total}
  target: < 100k for 3 tickets (vs 273k baseline = 63% savings)

# Batching optimization
batching:
  shared_planning: true  # One planning session for all 3
  planning_cost: 15k  # Shared across tickets
  per_ticket_savings: ~5k planning overhead
EOF

# 3. Prepare workspaces
.local/setup-agent-workspace.sh worker-a
.local/setup-agent-workspace.sh worker-b  # If parallel
```

### Execution

```yaml
Approach: Adaptive (data-driven from A1/A2)
Tickets: R03 + R06 + R07 (batched)
Optimization: Shared planning, parallel execution

Steps:
  1. Shared planning session (analyze all 3 tickets)
  2. Spawn workers with model assignments
  3. Monitor execution and quality
  4. Validate batching efficiency
  5. Compare to unbatched baseline
```

### Data Collection

```yaml
# docs/orchestration-data/phase-a3-phase1/execution-log.yaml

shared_planning:
  model: sonnet
  tickets: [R03, R06, R07]
  estimated_tokens: 15000  # Shared cost
  activities:
    - Analyze 3 tickets
    - Classify complexity
    - Assign models per ticket
    - Create all tasks

tickets:
  R03:
    execution_model: ${MODEL_FROM_A2}
    estimated_tokens: ${ESTIMATE}
    actual_tokens: ${ACTUAL}
    fix_loops: ${FIXES}

  R06:
    execution_model: ${MODEL_FROM_A2}
    estimated_tokens: ${ESTIMATE}
    actual_tokens: ${ACTUAL}
    fix_loops: ${FIXES}

  R07:
    execution_model: ${MODEL_FROM_COMPLEXITY}
    estimated_tokens: ${ESTIMATE}
    actual_tokens: ${ACTUAL}
    fix_loops: ${FIXES}

totals:
  planning: 15000  # Shared
  execution: ${SUM_EXECUTION}
  monitoring: ${SUM_MONITORING}
  review: ${SUM_REVIEW}
  finalization: ${SUM_FINALIZATION}
  total: ${TOTAL}

  per_ticket_average: ${TOTAL / 3}
  savings_vs_baseline: ((273000 - TOTAL) / 273000) * 100

batching_efficiency:
  unbatched_cost: ${3 × 32000} = 96k
  batched_cost: ${TOTAL}
  batching_savings: ${UNBATCHED - BATCHED}
```

### Success Criteria

```yaml
Token Efficiency:
  - Target: < 100,000 tokens for 3 tickets
  - Stretch: < 80,000 tokens
  - ✅ PASS if: total_tokens < 100000

Per-Ticket Average:
  - Target: < 35,000 per ticket
  - ✅ PASS if: per_ticket_average < 35000

Batching Efficiency:
  - Unbatched: 96,000 tokens (3 × 32k)
  - Batched: ${total}
  - ✅ PASS if: batching_savings > 0

Quality Maintained:
  - All 3 tickets pass review
  - ✅ PASS if: all_reviews_passed

Comparison to Baseline:
  - V2 baseline: 273,000 tokens (3 × 121k unbatched)
  - Savings: ${savings_percent}%
  - ✅ PASS if: savings >= 60%
```

---

## Data Analysis Tools

### Compare All Phases

```bash
# Create comparison script
cat > docs/orchestration-data/compare-phases.py <<'EOF'
#!/usr/bin/env python3
"""Compare results across Phase A1, A2, and A3."""

import yaml
import json
from pathlib import Path

def load_phase_data(phase_dir):
    """Load execution log and analysis for a phase."""
    log_file = phase_dir / "execution-log.yaml"
    analysis_file = phase_dir / "analysis.md"

    with open(log_file) as f:
        data = yaml.safe_load(f)

    return data

def compare_phases():
    """Generate comparison table."""
    base_dir = Path("docs/orchestration-data")

    a1 = load_phase_data(base_dir / "phase-a1-r03")
    a2 = load_phase_data(base_dir / "phase-a2-r06")
    a3 = load_phase_data(base_dir / "phase-a3-phase1")

    print("# Phase A Comparison\n")
    print("| Metric | A1 (Conservative) | A2 (Experimental) | A3 (Adaptive) |")
    print("|--------|-------------------|-------------------|---------------|")
    print(f"| Tickets | 1 (R03) | 1 (R06) | 3 (R03+R06+R07) |")
    print(f"| Execution model | Sonnet | Haiku | Data-driven |")
    print(f"| Total tokens | {a1['totals']['total_tokens']:,} | {a2['totals']['total_tokens']:,} | {a3['totals']['total']:,} |")
    print(f"| Per-ticket avg | {a1['totals']['total_tokens']:,} | {a2['totals']['total_tokens']:,} | {a3['totals']['total'] // 3:,} |")
    print(f"| Fix loops | {a1['implementation']['fix_loops']} | {a2['implementation']['fix_loops']} | Avg: {a3['avg_fix_loops']} |")
    print(f"| Quality | {a1['review']['result']} | {a2['review']['result']} | All PASS |")
    print(f"| Savings vs V2 | {a1['totals']['savings_percent']}% | {a2['totals']['savings_percent']}% | {a3['totals']['savings_vs_baseline']}% |")

    print("\n## Model Performance Summary\n")
    print(f"- Sonnet success rate: {calculate_success_rate('sonnet')}%")
    print(f"- Haiku success rate: {calculate_success_rate('haiku')}%")
    print(f"- Recommended execution model: {recommend_model()}")

if __name__ == "__main__":
    compare_phases()
EOF

chmod +x docs/orchestration-data/compare-phases.py
```

### Token Cost Tracking

```bash
# Create token tracking dashboard
cat > docs/orchestration-data/token-dashboard.sh <<'EOF'
#!/bin/bash
"""Real-time token cost tracking during execution."""

echo "=== Token Cost Dashboard ==="
echo
echo "Phase A1 (R03 - Conservative):"
echo "  Target: 32,000 tokens"
echo "  Actual: [monitoring...]"
echo
echo "Phase A2 (R06 - Experimental):"
echo "  Target: 28,000 tokens (40k with fix)"
echo "  Actual: [not started]"
echo
echo "Phase A3 (R03+R06+R07 - Adaptive):"
echo "  Target: 80,000 tokens"
echo "  Actual: [not started]"
echo
echo "Savings vs V2 Baseline:"
echo "  Baseline: 394,000 tokens (121k × 3 + 121k)"
echo "  Optimized: [calculating...]"
echo "  Savings: [calculating...]%
EOF

chmod +x docs/orchestration-data/token-dashboard.sh
```

---

## Quick Start Guide

### Execute Phase A1 Now

```bash
# 1. Setup
mkdir -p docs/orchestration-data/phase-a1-r03
.local/setup-agent-workspace.sh worker-a

# 2. Start session with data collection
cat > /tmp/phase-a1-prompt.txt <<EOF
Execute Phase A1 validation for R03 using the orchestration-lead skill.

Strategy: phase="A1_conservative"
- Sonnet planning
- Sonnet execution
- Haiku monitoring
- T1 checklist review

Track token usage carefully. I'll manually record metrics after execution.
EOF

# 3. Execute (manual)
# Use orchestration-lead skill in Claude Code session
# Monitor token usage in real-time

# 4. After execution, record data
vim docs/orchestration-data/phase-a1-r03/execution-log.yaml
# Fill in actual token costs, fix loops, quality results

# 5. Analyze
vim docs/orchestration-data/phase-a1-r03/analysis.md
# Assess results, decide on Phase A2 approach

# 6. Decision point
if grep -q "✅ YES" docs/orchestration-data/phase-a1-r03/analysis.md; then
    echo "✅ Phase A1 success! Ready for Phase A2"
    # Proceed to Phase A2 setup
else
    echo "❌ Phase A1 needs refinement"
    # Iterate on A1
fi
```

---

## Summary

**Phase A1:** Prove conservative approach works (32k tokens, 74% savings)
**Phase A2:** Test Haiku execution quality (28k target, validate or reject)
**Phase A3:** Optimize with real data (30-35k adaptive, production-ready)

**Data Collection:** Manual + automated extraction from session transcripts
**Decision Making:** Empirical thresholds (success rate, token costs, quality)
**Outcome:** Production-ready adaptive model selection strategy validated with real data
