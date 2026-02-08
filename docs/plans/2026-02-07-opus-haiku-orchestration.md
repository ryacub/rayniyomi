# Adaptive Multi-Model Orchestration Framework

**Goal:** Maximize quality while optimizing token costs through intelligent model selection.

**Strategy:** Match model to task complexity, validate with data, upgrade on failure.

---

## Core Principle

**Use the right model for the right job. Let quality and data guide optimization.**

### Adaptive Model Selection Strategy

```
┌──────────────────────────────────────────┐
│ 1. Task Analysis                         │
│    - Complexity (mechanical vs nuanced)  │
│    - Risk (can mistakes be caught?)      │
│    - Reference availability              │
└──────────────────────────────────────────┘
                 ↓
┌──────────────────────────────────────────┐
│ 2. Model Selection (Dynamic)             │
│    Haiku (8k):    Mechanical, low-risk   │
│    Sonnet (12k):  Default, proven        │
│    Opus (20k):    Strategic, complex     │
└──────────────────────────────────────────┘
                 ↓
┌──────────────────────────────────────────┐
│ 3. Execution with Quality Gates          │
│    - T1 Checklist (catches 80% issues)   │
│    - T2 Expert Review                    │
│    - T3 Full Debate                      │
└──────────────────────────────────────────┘
                 ↓
┌──────────────────────────────────────────┐
│ 4. Progressive Upgrade on Failure        │
│    Simple error → Same model             │
│    Logic error  → Upgrade (Haiku→Sonnet) │
│    Architecture → Upgrade (Sonnet→Opus)  │
└──────────────────────────────────────────┘
                 ↓
┌──────────────────────────────────────────┐
│ 5. Data Collection & Learning            │
│    - Success rates by model              │
│    - Fix loop frequency                  │
│    - Actual token costs                  │
│    - Adjust strategy based on data       │
└──────────────────────────────────────────┘
```

### Three-Tier Model System

**Haiku (8k tokens) - Mechanical Work:**
- ✅ Git commands (branch creation, push, fetch)
- ✅ File operations (read, grep, compile)
- ✅ Monitoring and verification
- ✅ Simple pattern following with clear reference
- ⚠️  Risk: Medium for implementation (validate first)

**Sonnet (12k tokens) - Default Workhorse:**
- ✅ Implementation work (proven quality)
- ✅ Nuanced logic (coroutines, lifecycle, state)
- ✅ Planning for T1/T2 tickets
- ✅ Review for T2 tickets (android-expert)
- ✅ Risk: Low - established performance

**Opus (20k tokens) - Strategic Thinking:**
- ✅ T3 architectural planning
- ✅ Complex multi-ticket coordination
- ✅ Review synthesis (conflicting perspectives)
- ✅ High-stakes architectural decisions
- ✅ Risk: Low - highest capability

---

## Token Cost Analysis

### Baseline: V2 All-Sonnet (121k tokens)
```
Lead spawn:           15,000 tokens
Worker spawn:         15,000 tokens
Task monitoring:      10,000 tokens
Review debate:        66,000 tokens (android-expert + code-reviewer)
Finalization:         15,000 tokens
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TOTAL:               121,000 tokens
Cost per ticket:     121,000 tokens
```

### Conservative: Sonnet + T1 Tier (32k tokens)
```
Sonnet planning:      10,000 tokens (good enough for T1)
Sonnet worker:        12,000 tokens (proven quality)
Haiku monitoring:      5,000 tokens (mechanical checks)
T1 checklist:          5,000 tokens (no debate)
Haiku finalization:    8,000 tokens (git commands)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TOTAL:                32,000 tokens (74% reduction)
Cost per ticket:      32,000 tokens
Risk:                 LOW - proven models
```

### Aggressive: Haiku Execution (28k tokens)
```
Sonnet planning:      10,000 tokens
Haiku worker:          8,000 tokens (experimental)
Haiku monitoring:      5,000 tokens
T1 checklist:          5,000 tokens
Haiku finalization:    8,000 tokens
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TOTAL:                28,000 tokens (77% reduction)
Cost if 1 fix loop:   40,000 tokens (67% reduction)
Cost if 2 fix loops:  52,000 tokens (57% reduction)
Risk:                 MEDIUM - needs validation
```

### Adaptive: Data-Driven Selection (30-35k tokens)
```
Planning:             10-12k tokens (Sonnet or Opus based on complexity)
Execution:            8-12k tokens (Haiku or Sonnet based on task)
Monitoring:           5k tokens (Haiku - mechanical)
Review:               5-76k tokens (T1/T2/T3 tiered)
Finalization:         8k tokens (Haiku - git commands)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TOTAL:                30-35k average (70-75% reduction)
Risk:                 LOW - validated choices
Quality:              HIGH - upgrade on failure
```

**Key insight:** Conservative approach (Sonnet) gives 74% savings with low risk. Aggressive approach (Haiku) gives 77% savings but unproven. Adaptive approach optimizes both.

---

## Phase A: Validation Strategy

**Goal: Measure actual performance before committing to a model strategy.**

### Three-Phase Validation

#### Phase A1: Conservative Baseline (R03)
```yaml
Approach: Sonnet planning + Sonnet execution
Target: 32k tokens, 0-1 fix loops
Measure:
  - Actual token cost
  - Quality (review gate pass rate)
  - Fix loops required
  - Time to completion
Success criteria: < 40k tokens, passes review on first or second attempt
```

#### Phase A2: Aggressive Experiment (R06)
```yaml
Approach: Sonnet planning + Haiku execution
Target: 28k tokens (40k with 1 fix loop acceptable)
Measure:
  - Haiku implementation quality
  - Fix loop frequency vs Sonnet
  - Types of errors (mechanical vs logic)
  - Effective cost (base + fix loops)
Success criteria: < 45k effective tokens, quality acceptable
```

#### Phase A3: Data-Driven Decision
```yaml
If Haiku quality good (0-1 fix loops):
  → Adopt Haiku for T1 mechanical tasks
  → Use Sonnet for T1 nuanced tasks
  → Effective cost: ~30k per ticket

If Haiku needs 2+ fix loops:
  → Stick with Sonnet for all implementation
  → Keep Haiku for monitoring only
  → Effective cost: ~32k per ticket

Either way: 70%+ savings achieved
```

### Success Metrics

**Token efficiency:**
- ✅ Target: < 40k per ticket (67% reduction)
- ✅ Stretch: < 35k per ticket (71% reduction)

**Quality maintained:**
- ✅ Review gates pass within 2 attempts
- ✅ No regressions vs single-agent quality
- ✅ Zero defects reach main branch

**Data collected:**
- ✅ Model success rates by task type
- ✅ Fix loop frequency
- ✅ Error patterns (syntax vs logic vs architecture)
- ✅ Effective cost per model choice

**Adaptive strategy validated:**
- ✅ Can predict which model to use
- ✅ Progressive upgrade works
- ✅ Quality gates catch issues
- ✅ Ready for Phase 1 (batched execution)

---

## Workflow Pattern

### Phase 1: Strategic Planning (10-20k tokens, model selected by complexity)

**Planning agent responsibilities (Sonnet for T1/T2, Opus for T3):**

1. **Read ticket requirements** (R03, R06, R07, etc.)
2. **Analyze complexity and select models:**
   ```python
   def select_models_for_ticket(ticket):
       complexity = analyze_complexity(ticket)

       if complexity == "mechanical":
           # Simple refactor, clear reference
           return {
               "planning": "sonnet",    # 10k
               "execution": "haiku",    # 8k (validate first)
               "review": "T1"           # 5k
           }
       elif complexity == "nuanced":
           # Lifecycle, coroutines, state
           return {
               "planning": "sonnet",    # 10k
               "execution": "sonnet",   # 12k (proven)
               "review": "T2"           # 25k
           }
       elif complexity == "architectural":
           # New patterns, major changes
           return {
               "planning": "opus",      # 20k (strategic)
               "execution": "sonnet",   # 12k
               "review": "T3"           # 76k
           }
   ```
3. **Decompose into precise tasks:**
   ```yaml
   Task #1: Branch creation
     Command: git checkout -b {branch} {sha}
     Verification: git branch --show-current == {branch}
     Success criteria: Branch exists, HEAD at correct SHA

   Task #2: Implementation
     Files to modify: [exact paths]
     Pattern to follow: [reference implementation]
     Lines to change: [specific line ranges]
     Commit message: "R03: [exact message]"
     Success criteria: Code compiles, follows pattern
   ```

4. **Define review criteria:**
   - For T1: Lead review checklist (no debate needed)
   - For T2: Android-expert review required
   - For T3: Full debate (architecture changes)

5. **Create fix requirements template:**
   - If review fails, what fixes are needed?
   - Pre-define common issues and solutions

6. **Output: Detailed task specifications with model selection**

### Phase 2: Adaptive Execution (8-12k tokens, model per task)

**Worker responsibilities (Haiku or Sonnet based on task metadata):**

1. **Poll TaskList** for available tasks
2. **Read task description** (already detailed by planner)
3. **Check task.metadata.execution_model:**
   - If "haiku": Execute mechanically (8k tokens)
   - If "sonnet": Execute with judgment (12k tokens)
4. **Execute steps:**
   - Follow explicit instructions
   - Run verification commands
   - Handle errors gracefully
5. **Report completion** with verification output
6. **Loop** until no tasks

**Why adaptive execution works:**
- **Haiku** for mechanical (git, compile, pattern-following)
  - Tasks are pre-decomposed
  - Instructions are explicit
  - Verification criteria are clear
  - Low cost, acceptable risk
- **Sonnet** for nuanced (logic, lifecycle, state)
  - Requires judgment
  - Handles edge cases
  - Proven quality
  - Worth the extra 4k tokens

### Phase 3: Haiku Lead Monitoring (5k tokens)

**Haiku lead responsibilities:**

1. **Monitor TaskList** for completed tasks
2. **Run verification commands:**
   ```bash
   git log --oneline -1  # Check commit exists
   git merge-tree ...    # Check conflicts
   ```
3. **For T1 tickets: Lead review directly** (no debate)
   - Check against Opus-defined criteria
   - Simple pass/fail decision
4. **For T2/T3: Escalate to specialized reviewers**
5. **Finalize:**
   - Push branch
   - Create PR
   - Merge
   - Update R-BOARD

**Why Haiku is sufficient:**
- Verification is mechanical (run commands, check output)
- T1 review criteria defined by Opus (checklist-based)
- Complex reviews escalated to experts
- Finalization is git commands (no decisions)

### Phase 4: Opus Review Synthesis (Only for T2/T3)

**Opus responsibilities:**

1. **Receive review results** from android-expert/code-reviewer
2. **Synthesize findings** into actionable fix requirements
3. **Create fix task** with detailed specifications
4. **Update review criteria** if needed

**Why Opus needed here:**
- Synthesizing conflicting perspectives requires judgment
- Translating review feedback into precise fix tasks
- Updating criteria based on findings

---

## Adaptive Model Selection Matrix

### By Task Type and Complexity

| Task Type | T1 (Simple) | T2 (Nuanced) | T3 (Architectural) |
|-----------|-------------|--------------|-------------------|
| **Planning** | Sonnet (10k) | Sonnet (12k) | Opus (20k) |
| **Reasoning** | Good enough | Good enough | Strategic needed |
| | | | |
| **Branch Creation** | Haiku (8k) | Haiku (8k) | Haiku (8k) |
| **Reasoning** | Pure git commands | Pure git commands | Pure git commands |
| | | | |
| **Implementation** | Haiku* (8k) | Sonnet (12k) | Sonnet (12k) |
| **Reasoning** | Validate first | Proven quality | Proven quality |
| | | | |
| **Monitoring** | Haiku (5k) | Haiku (5k) | Haiku (5k) |
| **Reasoning** | Mechanical checks | Mechanical checks | Mechanical checks |
| | | | |
| **Review** | T1: Checklist (5k) | T2: Expert (25k) | T3: Debate (76k) |
| **Reasoning** | Haiku checklist | Android-expert | Full debate + synthesis |
| | | | |
| **Review Synthesis** | N/A | Sonnet (10k) | Opus (10k) |
| **Reasoning** | Checklist only | Expert findings | Conflicting perspectives |
| | | | |
| **Finalization** | Haiku (8k) | Haiku (8k) | Haiku (8k) |
| **Reasoning** | Git commands | Git commands | Git commands |
| | | | |
| **Total Cost** | **~32k** | **~57k** | **~116k** |
| **vs Baseline** | **74% savings** | **53% savings** | **4% savings** |

\* Haiku for T1 implementation pending Phase A validation

---

## Progressive Upgrade Mechanism

**Key principle: Start with cheaper model, upgrade on failure.**

### Failure Classification and Response

```python
def handle_task_failure(task, failure_type, error_details):
    """Progressive model upgrade based on failure analysis."""

    if failure_type == "mechanical_error":
        # Syntax, missing import, simple typo
        # Same model can fix it
        return create_fix_task(
            model=task.execution_model,  # Same: Haiku or Sonnet
            description=f"Fix mechanical error: {error_details}"
        )

    elif failure_type == "logic_error":
        # Incorrect coroutine usage, lifecycle bug, state race
        # Upgrade: Haiku → Sonnet
        if task.execution_model == "haiku":
            return create_fix_task(
                model="sonnet",  # Upgrade
                description=f"Fix logic error (requires judgment): {error_details}",
                note="Upgraded from Haiku due to logic complexity"
            )
        else:
            # Already Sonnet, retry
            return create_fix_task(
                model="sonnet",
                description=f"Fix logic error: {error_details}"
            )

    elif failure_type == "architectural_error":
        # Fundamental approach wrong, pattern mismatch
        # Upgrade: Sonnet → Opus
        return create_fix_task(
            model="opus",  # Strategic rethink
            description=f"Rethink approach (architectural issue): {error_details}",
            note="Upgraded to Opus for strategic planning"
        )

    elif failure_type == "review_failed":
        # Review gate caught issues
        review_tier = task.metadata.review_tier

        if review_tier == "T1":
            # Checklist failed, mechanical fixes
            return create_fix_task(
                model=task.execution_model,  # Same model
                description=f"Fix checklist failures: {error_details}"
            )
        elif review_tier == "T2":
            # Android-expert found issues, likely needs Sonnet
            return create_fix_task(
                model="sonnet",  # Ensure quality
                description=f"Fix expert review issues: {error_details}"
            )
        elif review_tier == "T3":
            # Full debate found issues, may need architectural rethink
            severity = analyze_severity(error_details)
            model = "opus" if severity == "high" else "sonnet"
            return create_fix_task(
                model=model,
                description=f"Fix debate issues: {error_details}"
            )
```

### Cost Impact of Fix Loops

**Haiku execution with fix loops:**
```
Attempt 1 (Haiku):        8k
  └─ If fails: Fix (Sonnet): 12k
     TOTAL:                20k (still 83% savings vs 121k)

Attempt 1 (Haiku):        8k
  └─ If fails: Fix (Sonnet): 12k
     └─ If fails: Fix (Opus):  20k
        TOTAL:             40k (67% savings vs 121k)
```

**Key insight:** Even with 2 fix loops (Haiku → Sonnet → Opus), we still save 67% vs baseline.

**Break-even analysis:**
- Baseline: 121k
- Haiku path: 8k + (N × 12k) for N fix loops
- Break-even: N = 9 fix loops
- **Haiku is cost-effective even with significant failures**

### Data-Driven Model Selection

**After Phase A validation, track:**

```python
model_performance = {
    "haiku": {
        "tasks_attempted": 10,
        "first_attempt_success": 7,  # 70%
        "success_with_1_fix": 9,      # 90%
        "avg_cost": 28k,               # 8k + (0.3 × 12k fix)
        "use_for": ["branch", "mechanical_impl", "monitoring"]
    },
    "sonnet": {
        "tasks_attempted": 10,
        "first_attempt_success": 9,  # 90%
        "success_with_1_fix": 10,    # 100%
        "avg_cost": 32k,              # 12k + (0.1 × 12k fix)
        "use_for": ["planning", "nuanced_impl", "review"]
    },
    "opus": {
        "tasks_attempted": 2,
        "first_attempt_success": 2,  # 100%
        "avg_cost": 20k,
        "use_for": ["strategic_planning", "arch_decisions"]
    }
}

# Decision logic:
if task.type == "implementation":
    if task.complexity == "mechanical" and haiku_success_rate > 60%:
        return "haiku"  # Cost-effective
    else:
        return "sonnet"  # Proven quality
```

---

## Updated Orchestration-Lead Pattern

### Lead Agent Configuration

```python
# Lead agent uses Opus for planning
Task(
  subagent_type="general-purpose",
  model="opus",  # Strategic layer
  team_name="phase-a-v2",
  name="team-lead",
  prompt="""You are the strategic planning lead.

Your responsibilities:
1. Read ticket requirements (R03, R06, R07)
2. Decompose into explicit, executable tasks
3. Define review criteria by tier (T1/T2/T3)
4. Create task specifications with:
   - Exact commands to run
   - Verification criteria
   - Success/failure conditions
5. Spawn Haiku workers with task specifications
6. Monitor via Haiku monitoring agent
7. Synthesize reviews if T2/T3 needed

Token budget: 20k for planning
"""
)

# Monitoring agent uses Haiku (low cost)
Task(
  subagent_type="general-purpose",
  model="haiku",  # Execution layer
  team_name="phase-a-v2",
  name="monitor",
  prompt="""You are the monitoring agent.

Your responsibilities:
1. Poll TaskList every 10s
2. When task completes:
   - Run verification commands (git log, git merge-tree)
   - Check against success criteria from task metadata
3. For T1: Run lead review checklist
4. For T2/T3: Escalate to Opus for expert review coordination
5. If all checks pass: Execute finalization
6. If checks fail: Create fix task (using template from Opus)

Token budget: 5k for monitoring
"""
)
```

### Worker Agent Configuration

```python
# Workers use Haiku for execution
Task(
  subagent_type="android-expert",
  model="haiku",  # Low-cost execution
  team_name="phase-a-v2",
  name="worker-a",
  prompt="""You are Worker-A. Execute tasks exactly as specified.

POLLING LOOP:
1. TaskList() - find available task
2. Claim task: TaskUpdate(owner='worker-a', status='in_progress')
3. Read task description (has explicit commands)
4. Execute commands step-by-step
5. Run verification commands from task metadata
6. Report: TaskUpdate(status='completed') + message to monitor
7. Loop

Your workspace: /tmp/rayniyomi-workspaces/worker-a/

DO NOT:
- Make architectural decisions (follow task spec)
- Skip verification steps
- Push/PR/merge (monitor handles finalization)

Token budget: 8k per task cycle
"""
)
```

---

## Review Tier System

### T1: Lead Review Only (5k tokens)

**When to use:**
- Simple refactors (remove runBlocking → launchIO)
- Pattern following (copy existing implementation)
- No architecture changes
- < 50 lines changed

**Process:**
1. Haiku monitor runs checklist:
   ```
   ✓ Code compiles
   ✓ Follows reference pattern
   ✓ No new runBlocking calls
   ✓ Commit message matches format
   ✓ Only target files changed
   ```
2. If all checks pass → Finalize
3. If any fail → Create fix task

**Cost:** 5k (monitor checks + finalization)

### T2: Android-Expert Review (15k tokens)

**When to use:**
- Lifecycle changes (ViewModel, Activity)
- Coroutine scope changes
- State management updates
- 50-200 lines changed

**Process:**
1. Haiku monitor escalates to Opus
2. Opus spawns android-expert:
   ```
   Review for architecture, lifecycle, threading
   ```
3. Android-expert returns findings
4. Opus creates fix task if needed OR approves
5. Haiku monitor finalizes if approved

**Cost:** 15k (android-expert) + 10k (opus synthesis) = 25k

### T3: Full Debate (66k tokens)

**When to use:**
- Major architecture changes
- New patterns introduced
- Cross-cutting concerns
- > 200 lines changed

**Process:**
1. Haiku monitor escalates to Opus
2. Opus runs android-code-review-debate:
   - Android-expert review (15k)
   - Code-reviewer response (15k)
   - Opus synthesis (10k)
3. Opus creates detailed fix task OR approves
4. Haiku monitor finalizes if approved

**Cost:** 66k (full debate) + 10k (synthesis) = 76k

---

## Ticket Classification for Review Tiers

### Automatic Classification (in Opus Planning Phase)

```python
def classify_ticket(ticket_data):
    """Opus classifies ticket during planning."""

    # T1 indicators
    if (ticket_data.pattern == "simple refactor" and
        ticket_data.files_changed < 3 and
        ticket_data.lines_changed < 50 and
        ticket_data.has_reference_implementation):
        return "T1"

    # T3 indicators
    if (ticket_data.architecture_change or
        ticket_data.new_pattern or
        ticket_data.lines_changed > 200):
        return "T3"

    # Default T2
    return "T2"

# Examples:
classify_ticket(R03) → "T1"  # Simple refactor, follows R05 pattern
classify_ticket(R06) → "T2"  # Lifecycle change, needs android-expert
classify_ticket(R18) → "T3"  # Architecture change (validation layer)
```

### Opus Task Creation Includes Tier

```yaml
TaskCreate({
  subject: "R03: Implement async ReaderViewModel",
  metadata: {
    ticket: "R03",
    review_tier: "T1",  # Set by Opus during planning
    review_checklist: [
      "Code compiles",
      "Follows setMangaOrientationType pattern",
      "No withUIContext wrapper (unnecessary)",
      "Uses viewModelScope.launchIO",
      "Commit message: 'R03: make setMangaReadingMode non-blocking'"
    ]
  }
})
```

---

## Cost Comparison by Scenario

### Scenario 1: Single T1 Ticket (R03)

**Current (All Sonnet):**
```
Lead spawn:           15k
Worker spawn:         15k
Monitoring:           10k
Review debate:        66k
Finalization:         15k
━━━━━━━━━━━━━━━━━━━━━━━━━
TOTAL:               121k
```

**Optimized (Opus Planning + Haiku Execution):**
```
Opus planning:        20k
Haiku worker:          8k
Haiku monitoring:      5k (includes T1 review)
Haiku finalization:    8k
━━━━━━━━━━━━━━━━━━━━━━━━━
TOTAL:                41k (66% reduction)
```

### Scenario 2: Three T1 Tickets Batched (R03 + R06 + R07)

**Current (All Sonnet):**
```
Lead spawn:           15k
Worker spawn:         15k
3 × (Monitoring + Review):  3 × 76k = 228k
Finalization:         15k
━━━━━━━━━━━━━━━━━━━━━━━━━
TOTAL:               273k
```

**Optimized (Opus Planning + Haiku Execution):**
```
Opus planning (all 3): 30k (shared planning cost)
Haiku worker:          8k (one worker handles all 3)
3 × (Monitoring + T1 Review): 3 × 5k = 15k
3 × Finalization:      3 × 8k = 24k
━━━━━━━━━━━━━━━━━━━━━━━━━
TOTAL:                77k (72% reduction)
```

**Key insight:** Batching amplifies savings because Opus planning is shared.

### Scenario 3: Mixed Tiers (R03 T1 + R06 T2 + R18 T3)

**Current (All Sonnet):**
```
Lead spawn:           15k
Worker spawn:         15k
R03: 76k (debate)
R06: 76k (debate)
R18: 76k (debate)
Finalization:         15k
━━━━━━━━━━━━━━━━━━━━━━━━━
TOTAL:               273k
```

**Optimized (Tiered Reviews):**
```
Opus planning:        30k
Haiku worker:          8k
R03 (T1): 5k
R06 (T2): 25k (android-expert + synthesis)
R18 (T3): 76k (full debate)
Finalization:         24k
━━━━━━━━━━━━━━━━━━━━━━━━━
TOTAL:               168k (38% reduction)
```

**Key insight:** Tier system avoids over-engineering simple tickets.

---

## Implementation Steps

### Step 1: Update Orchestration-Lead Skill

Add model selection logic:

```python
# In orchestration-lead skill

def spawn_strategic_lead():
    """Spawn Opus for planning."""
    Task(
        subagent_type="general-purpose",
        model="opus",  # Strategic layer
        name="strategic-lead",
        prompt=STRATEGIC_PLANNING_PROMPT
    )

def spawn_execution_workers(count=1):
    """Spawn Haiku workers for execution."""
    for i in range(count):
        Task(
            subagent_type="android-expert",
            model="haiku",  # Execution layer
            name=f"worker-{chr(97+i)}",
            prompt=WORKER_POLLING_PROMPT
        )

def spawn_monitor():
    """Spawn Haiku for monitoring."""
    Task(
        subagent_type="general-purpose",
        model="haiku",  # Low-cost monitoring
        name="monitor",
        prompt=MONITOR_PROMPT
    )
```

### Step 2: Create Ticket Classification Logic

```python
# In Opus strategic planning agent

def classify_tickets(tickets):
    """Classify tickets by review tier."""
    classifications = {}

    for ticket in tickets:
        # Read ticket requirements
        requirements = read_ticket(ticket)

        # Analyze complexity
        tier = determine_tier(
            files_changed=requirements.files,
            lines_estimate=requirements.lines,
            has_reference=requirements.reference_impl,
            architecture_change=requirements.arch_change
        )

        # Create review checklist
        checklist = generate_checklist(ticket, tier)

        classifications[ticket] = {
            "tier": tier,
            "checklist": checklist,
            "estimated_tokens": TIER_COSTS[tier]
        }

    return classifications
```

### Step 3: Update Task Creation Pattern

```python
# Opus strategic lead creates tasks with tier metadata

TaskCreate({
    subject: f"{ticket}: Implement changes",
    description: f"""
    Implement {ticket} following this pattern:

    Files to modify:
    - {file1}: Lines {start}-{end}
    - {file2}: Lines {start}-{end}

    Reference implementation: {reference_file}:{line}

    Steps:
    1. Read reference: {reference_file}
    2. Apply pattern to {target_file}
    3. Verify compilation: ./gradlew :app:compileDebugKotlin
    4. Commit: git commit -m "{commit_msg}"

    Verification commands:
    - git log --oneline -1 | grep "{ticket}"
    - git diff HEAD~1 --stat | grep "{expected_files}"
    """,
    metadata: {
        "ticket": ticket,
        "review_tier": tier,  # T1/T2/T3
        "review_checklist": checklist,
        "reference_impl": reference_file,
        "expected_files": expected_files
    }
})
```

### Step 4: Update Monitor Logic

```python
# Haiku monitor checks tier and routes accordingly

def handle_completed_task(task):
    """Route based on review tier."""

    tier = task.metadata.review_tier

    if tier == "T1":
        # Run checklist directly (Haiku capable)
        result = run_t1_checklist(task)
        if result.passed:
            finalize(task)
        else:
            create_fix_task(task, result.failures)

    elif tier == "T2":
        # Escalate to Opus for android-expert coordination
        escalate_to_opus(task, review_type="android-expert")

    elif tier == "T3":
        # Escalate to Opus for full debate
        escalate_to_opus(task, review_type="debate")
```

### Step 5: Validate with Phase A

Test with R03 (T1 ticket):
```
1. Opus plans R03 (20k tokens)
2. Haiku worker executes (8k tokens)
3. Haiku monitor runs T1 checklist (5k tokens)
4. Haiku finalizes (8k tokens)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TOTAL: 41k tokens vs 121k (66% savings)
```

---

## Success Metrics

### Token Efficiency
- ✅ Single T1 ticket: < 50k tokens (vs 121k baseline)
- ✅ Batched T1 tickets: < 30k per ticket (vs 121k)
- ✅ Mixed tiers: Only pay for complexity needed

### Quality Maintained
- ✅ T1 checklist catches common issues
- ✅ T2/T3 get expert reviews when needed
- ✅ No shortcuts on complex tickets

### Execution Speed
- ✅ Haiku workers faster than Sonnet
- ✅ T1 review instant (no debate latency)
- ✅ Batching reduces overhead

---

## Migration Path

### Phase 1: Validate T1 Pattern
1. Run R03 with Opus planning + Haiku execution
2. Measure tokens and quality
3. Compare to baseline

### Phase 2: Implement Tier System
1. Add tier classification to Opus planning
2. Update monitor to route by tier
3. Test T2 with R06

### Phase 3: Batch Processing
1. Run R03 + R06 + R07 as batch
2. Validate shared Opus planning
3. Measure token savings

### Phase 4: Production Rollout
1. Execute Phase 1 (R03 + R06 + R07) with optimized pattern
2. Execute Phase 2 (R10 + R11 + R13) with batching
3. Monitor token usage and adjust tiers as needed

---

## Key Takeaways

1. **Opus for Strategy, Haiku for Execution**
   - Opus decomposes tickets into explicit tasks
   - Haiku follows instructions (no decisions needed)
   - Cost reduction: 66% for single tickets, 72% for batches

2. **Review Tier System**
   - T1 (5k): Lead checklist for simple refactors
   - T2 (25k): Android-expert for lifecycle/coroutines
   - T3 (76k): Full debate for architecture changes
   - Avoids over-engineering simple tickets

3. **Batching Amplifies Savings**
   - Single ticket: 121k → 41k (66% reduction)
   - Three tickets: 273k → 77k (72% reduction)
   - Opus planning cost is shared across batch

4. **Quality Maintained**
   - T1 checklist catches common issues
   - T2/T3 escalate to experts when needed
   - No shortcuts on complexity

5. **Implementation is Iterative**
   - Phase 1: Validate T1 with R03
   - Phase 2: Add tier system
   - Phase 3: Test batching
   - Phase 4: Production rollout

**Next action:** Update orchestration-lead skill with model selection logic and tier routing.
