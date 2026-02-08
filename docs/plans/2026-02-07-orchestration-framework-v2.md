# Multi-Agent Orchestration Framework V2

**Fixing the fundamental design flaw: Workers can't approve their own work.**

---

## What Went Wrong in Phase A

### Attempt 1: Message-Based Coordination (FAILED)
**Problem:** Workers don't auto-process messages, need explicit polling.

### Attempt 2: Task-Based with Polling Loop (PARTIAL SUCCESS)
**What worked:**
- ✅ Worker polling loop executed perfectly
- ✅ All tasks completed autonomously
- ✅ Branch created from clean SHA
- ✅ Implementation completed
- ✅ PR created

**What failed:**
- ❌ PR has implementation issues (found by android-expert)
- ❌ Lead never ran reviews before PR creation
- ❌ Worker jumped straight from implementation to PR

**Root cause:** Task pipeline had no review gate between implementation and PR creation.

---

## Core Principle: Separation of Execution and Approval

**Workers execute. Lead approves.**

```
┌─────────────────────────────────────────────┐
│ WORKERS: Execute discrete, verifiable tasks│
│ - Create branches                           │
│ - Write code                                │
│ - Run builds                                │
│ - Push branches (after approval)            │
└─────────────────────────────────────────────┘
                    ↓ Report completion
┌─────────────────────────────────────────────┐
│ LEAD: Verify, review, approve               │
│ - Verify task completion (git log, etc.)   │
│ - Run quality gates (conflict check, review)│
│ - Approve next phase                        │
│ - Update R-BOARD                            │
└─────────────────────────────────────────────┘
```

**Workers should NEVER:**
- ❌ Approve their own work
- ❌ Skip review gates
- ❌ Push to remote without lead approval
- ❌ Create PRs without lead approval
- ❌ Merge PRs

---

## Corrected Architecture: Two-Phase Task Model

### Phase 1: Worker Execution Tasks

Tasks workers can autonomously claim and execute:

```yaml
Task #1: "Branch creation"
  - Worker claims
  - Worker creates branch from SHA in metadata
  - Worker reports: "Branch created: {name} from {sha}"
  - Status: completed

Task #2: "Implementation"
  - Worker claims (after #1 completes)
  - Worker implements changes
  - Worker runs verification (compile)
  - Worker commits changes
  - Worker reports: "Implementation complete. Commit: {sha}"
  - Status: completed
```

### Phase 2: Lead Approval Tasks

Tasks ONLY lead can execute:

```yaml
[Lead monitors TaskList for worker completion]

When Task #2 completes:
  1. Lead verifies via git log (commit exists?)
  2. Lead runs pre-merge conflict check
  3. Lead runs android-code-review-debate
  4. Lead decision:
     ├─ IF reviews PASS:
     │   ├─ Lead pushes branch to ryacub
     │   ├─ Lead creates PR via gh CLI
     │   ├─ Lead merges PR
     │   ├─ Lead updates R-BOARD
     │   └─ Lead broadcasts completion
     │
     └─ IF reviews FAIL:
         ├─ Lead creates Task #3: "Fix review issues"
         ├─ Lead adds review feedback to task description
         └─ Worker claims #3, fixes issues, loop back
```

**Key insight:** Lead doesn't create tasks for review gates. Lead EXECUTES reviews directly when workers complete implementation.

---

## Corrected Task Decomposition Pattern

### Bad (What We Did)

```yaml
R03-branch-creation     # Worker task
  ↓
R03-implementation      # Worker task
  ↓
R03-pr-creation        # Worker task ← PROBLEM: Worker bypasses reviews!
```

### Good (What We Should Do)

```yaml
R03-branch-creation     # Worker task
  ↓
R03-implementation      # Worker task
  ↓
[STOP - No more worker tasks]

Lead monitors completion of R03-implementation:
  → Lead verifies
  → Lead reviews
  → IF PASS: Lead pushes, creates PR, merges (NOT worker)
  → IF FAIL: Lead creates R03-fix-issues task, worker claims
```

**Only 2 worker tasks, not 3.**

---

## Implementation Pattern

### Lead Agent Workflow

```python
# 1. Decompose ticket into worker-executable tasks ONLY
TaskCreate({
  id: "R03-branch-creation",
  description: "Create branch from SHA...",
  metadata: { sha: "...", branch: "..." }
})

TaskCreate({
  id: "R03-implementation",
  blockedBy: ["R03-branch-creation"],
  description: "Implement R03 per acceptance criteria..."
})

# 2. Spawn worker with polling loop
spawn_worker(name="worker-a", team="phase-a")

# 3. Monitor TaskList for completion
while True:
  tasks = TaskList()

  for task in tasks:
    if task.id == "R03-implementation" and task.status == "completed":
      # Worker finished implementation

      # Step 1: Verify via git
      commit = bash("cd workspace && git log --oneline -1")
      assert "R03" in commit, "Commit missing!"

      # Step 2: Run conflict check
      conflicts = bash("git merge-tree ...")
      if conflicts:
        create_fix_task("R03-fix-conflicts", conflicts)
        continue  # Worker will claim fix task

      # Step 3: Run review debate
      review = run_android_code_review_debate()
      if review.verdict == "REQUEST_CHANGES":
        create_fix_task("R03-fix-review-issues", review.issues)
        continue  # Worker will claim fix task

      # Step 4: All gates passed - Lead finalizes
      bash("cd workspace && git push ryacub {branch}")
      pr_url = bash("gh pr create --title '...' --body '...'")
      bash("gh pr merge {pr_number} --squash")
      update_rboard("R03")
      update_main_sha()
      broadcast("R03 complete")

      break  # Done with this ticket

  sleep(10)  # Poll every 10 seconds
```

### Worker Agent Workflow (Unchanged)

```python
# Polling loop (same as before)
while True:
  tasks = TaskList()
  task = find_task(status="pending", owner=None, not_blocked=True)

  if task:
    TaskUpdate(task.id, owner="worker-a", status="in_progress")
    execute_task(task)
    TaskUpdate(task.id, status="completed")
    report_to_lead(f"Task {task.id} complete")
    continue  # Look for next task

  # No tasks available
  sleep(30)
  retry_count += 1
  if retry_count > 3:
    exit("All tasks complete")
```

---

## Why This Works

### 1. Clear Responsibility Separation
- **Workers:** Execute code, run builds, report results
- **Lead:** Verify, review, approve, finalize

### 2. Review Gates Are Enforced
- Lead runs reviews AFTER implementation completes
- Lead blocks progression if reviews fail
- Workers can't bypass reviews

### 3. Automatic Fix Loop
- If reviews fail, lead creates fix task
- Worker claims fix task (just another task)
- Lead re-reviews after fix
- Loop until reviews pass

### 4. Atomic Finalization
- Only lead pushes to remote
- Only lead creates PRs
- Only lead merges
- Only lead updates R-BOARD

### 5. Token Efficiency
- Workers do heavy lifting (implementation)
- Lead does quick verification/review
- No unnecessary spawns for push/PR/merge

---

## Updated Loop-Closing Protocol

### Worker Task Completion Loop

```
1. Worker claims task
2. Worker executes task
3. Worker marks task completed in TaskList
4. Worker reports to lead via message
5. Lead verifies TaskList status (completed?)
6. Lead verifies actual result (git log, file exists, etc.)
7. ✅ Loop closed for worker task
```

### Lead Review Gate Loop

```
8. Lead runs conflict check
9. Lead records result (pass/fail)
10. Lead runs code review debate
11. Lead records result (approve/request changes)
12. IF fail: Lead creates fix task → back to worker loop (step 1)
13. IF pass: Lead continues to finalization
14. ✅ Loop closed for review gate
```

### Lead Finalization Loop

```
15. Lead pushes branch
16. Lead verifies branch on remote (git ls-remote)
17. Lead creates PR
18. Lead verifies PR exists (gh pr view)
19. Lead merges PR
20. Lead verifies merge (gh pr view --json state)
21. Lead updates R-BOARD
22. Lead verifies R-BOARD (grep check)
23. Lead broadcasts completion
24. Lead verifies workers received broadcast
25. ✅ Loop closed for finalization
```

**Every step has verification. Every async action waits for confirmation.**

---

## Corrected Phase A Implementation

### What We Should Have Done

```python
# Task decomposition (only 2 tasks)
TaskCreate("R03-branch-creation")  # Worker task
TaskCreate("R03-implementation", blockedBy=["R03-branch-creation"])  # Worker task
# NO Task #3 for PR creation!

# Spawn worker
spawn_worker("worker-a")

# Lead monitoring loop
while True:
  if task_completed("R03-implementation"):
    # Lead takes over
    verify_implementation()  # git log check

    if not conflict_check_passes():
      create_fix_task("R03-fix-conflicts")
      continue

    if not code_review_passes():
      create_fix_task("R03-fix-review")
      continue

    # All gates passed - Lead finalizes
    push_branch()
    create_pr()
    merge_pr()
    update_rboard()
    break
```

---

## Key Takeaways

### What We Learned

1. **Task-based polling works** - Workers autonomously claim and execute tasks
2. **But workers can't approve their own work** - Need review gates
3. **Review gates belong to lead, not workers** - Lead runs them between phases
4. **Only 2 task types needed:**
   - Setup tasks (branch creation)
   - Work tasks (implementation)
   - NO finalization tasks (lead does this)

### What to Fix

1. **Update orchestration-lead skill** - Remove PR creation tasks, add review gate logic
2. **Update worker spawn prompt** - Clarify they never push/PR/merge
3. **Document review gate pattern** - Lead monitors, reviews, finalizes
4. **Update task decomposition examples** - Show only worker-executable tasks

### Success Criteria for V2

Phase A should work like this:
- ✅ Worker creates branch (verified by lead)
- ✅ Worker implements code (verified by lead)
- ✅ Lead runs conflict check (blocks if fails)
- ✅ Lead runs code review (blocks if fails)
- ✅ Lead pushes, creates PR, merges (atomic)
- ✅ Lead updates R-BOARD
- ✅ Zero branch pollution
- ✅ Zero unapproved PRs

---

## Next Steps

1. **Abort current PR #107** - Has issues, created without approval
2. **Update framework docs** with this V2 pattern
3. **Retry Phase A** with corrected task decomposition
4. **Validate:** Worker stops after implementation, lead reviews, lead finalizes
5. **Document:** This is the canonical pattern going forward

**The framework is fixable. We just need 2 worker tasks, not 3, and lead handles finalization.**
