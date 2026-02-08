# Multi-Agent Orchestration System Architecture

**Properly designed system for coordinating multiple agents on parallel tickets.**

---

## 1. Core Requirements

### Functional Requirements
1. **Parallel execution** - Multiple agents work on different tickets simultaneously
2. **Git coordination** - Prevent branch pollution through centralized control
3. **Loop closure** - Every action confirmed before proceeding
4. **State visibility** - Lead knows worker state at all times
5. **Failure recovery** - Handle worker failures gracefully

### Non-Functional Requirements
1. **Token efficiency** - Minimize coordination overhead
2. **Low latency** - Fast response to worker requests
3. **Debuggability** - Clear audit trail of all actions
4. **Simplicity** - Easy to understand and maintain

---

## 2. Claude Code Agent Model (Reality Check)

### How Claude Code Agents Actually Work

**Agent Lifecycle:**
```
Spawn → Execute turn → Send message/complete → Go idle → [Wait for resume]
```

**Key Behaviors:**
- Agents execute **one turn** per invocation
- After sending a message, agent goes **idle**
- Messages TO lead are **auto-delivered** (lead sees them immediately)
- Messages TO workers are **queued in inbox** but don't auto-wake workers
- Workers must be **explicitly resumed** to process inbox and continue

**This means:**
```
❌ Lead sends message → Worker auto-wakes → Worker acts
✅ Lead sends message → Worker stays idle → Lead resumes worker → Worker acts
```

### Implications

1. **No continuous workers** - Workers don't stay active waiting for messages
2. **Explicit resumption required** - Lead must resume workers to continue work
3. **Message-response not synchronous** - Each exchange requires resume
4. **State must be durable** - Workers may restart between actions

---

## 3. Architecture Options

### Option A: Task-Per-Turn Model (Simple)

**Concept:** Each worker turn completes one discrete task.

```
Lead: Spawn worker(task="Create branch from SHA abc123")
Worker: [Creates branch, returns result]
Worker: "Branch created: claude/r03-async-reader-mode"

Lead: [Receives result, verifies]
Lead: Spawn worker(task="Implement R03 in branch")
Worker: [Implements, returns result]
Worker: "Implementation complete. Ready for review."

Lead: [Reviews code]
Lead: Spawn worker(task="Push branch and create PR")
Worker: [Pushes, creates PR, returns URL]
Worker: "PR created: https://github.com/.../pull/110"

Lead: [Merges PR, updates board]
```

**Pros:**
- ✅ Simple - no resume complexity
- ✅ Clear turn boundaries
- ✅ Easy to debug (discrete tasks)
- ✅ Natural confirmation (turn completion = success)

**Cons:**
- ❌ Higher token cost (multiple spawns)
- ❌ Lost context between turns
- ❌ More coordination overhead

**Loop Closure:** Each spawn-complete is a closed loop.

---

### Option B: Persistent Agent with Manual Resume (Medium Complexity)

**Concept:** Spawn worker once, resume for each coordination point.

```
Lead: Spawn worker(full-context, ticket=R03)
Worker: "Request branch creation for R03"
Worker: [Goes idle]

Lead: [Receives request]
Lead: Resume worker(message="Approved. SHA: abc123, Branch: r03-branch")
Worker: [Wakes, processes message, creates branch]
Worker: "Branch created from SHA abc123. Starting implementation."
Worker: [Goes idle]

Lead: [Receives confirmation]
[Worker works autonomously in background? Or needs resume to continue?]

Worker: [After implementation]
Worker: "Request merge approval for R03"
Worker: [Goes idle]

Lead: [Runs reviews]
Lead: Resume worker(message="Approved for merge. Push and create PR.")
Worker: [Pushes, creates PR]
Worker: "PR created: https://github.com/.../pull/110"
Worker: [Goes idle]

Lead: [Merges, updates board]
Lead: Resume worker(message="R03 complete. Token usage?")
Worker: "Token usage: 87k. Available for next assignment."
```

**Pros:**
- ✅ Maintains agent context across turns
- ✅ Lower spawn cost than Option A
- ✅ Flexible for complex workflows

**Cons:**
- ❌ Requires explicit resume management
- ❌ Lead must know when to resume
- ❌ Worker autonomy unclear (when do they need resume?)

**Loop Closure:** Resume + response = closed loop.

**Open Questions:**
- Can worker work autonomously between resumes?
- How long can worker stay idle before context is lost?
- How does lead know when worker needs resume vs. is working?

---

### Option C: Autonomous Worker with Polling (Complex)

**Concept:** Worker programmed to continuously check for work and messages.

```
Lead: Spawn worker(full-context, ticket=R03, mode="autonomous")
Worker: [Enters continuous loop]

While not complete:
  1. Check inbox for messages from lead
  2. If message: process and respond
  3. If waiting for approval: send request and sleep
  4. If approved: continue work
  5. Sleep 1 minute, repeat

Worker: "Request branch creation for R03"
[Sleep, polling inbox]

Lead: Send message("Approved. SHA: abc123")

Worker: [Polls inbox, sees message]
Worker: "Branch created from SHA abc123. Starting implementation."
Worker: [Implements code]
Worker: "Request merge approval for R03"
[Sleep, polling inbox]

Lead: Send message("Approved for merge")

Worker: [Polls inbox, sees message]
Worker: [Pushes, creates PR]
Worker: "PR created: https://github.com/.../pull/110"
```

**Pros:**
- ✅ True asynchronous coordination
- ✅ Worker autonomy (don't need resume)
- ✅ Natural message-based flow

**Cons:**
- ❌ Complex worker programming (polling loop)
- ❌ Higher token cost (continuous polling)
- ❌ May hit turn limits or timeouts
- ❌ Inbox polling might not work in Claude Code model

**Loop Closure:** Message + polled response = closed loop.

**Feasibility Question:** Can Claude Code agents actually poll and stay active? Or do they complete turn after each action?

---

### Option D: TaskList-Based Coordination (Async)

**Concept:** No direct messaging. State machine via shared TaskList.

```
Lead: Create TaskList tasks:
  - R03-1: Create branch (pending, assigned: worker-a)
  - R03-2: Implement (blocked by R03-1)
  - R03-3: Create PR (blocked by R03-2)
  - R03-4: Done (blocked by R03-3)

Lead: Spawn worker-a(mode="task-processor")

Worker-a:
  While true:
    tasks = TaskList.get(assigned=me, status=pending, unblocked)
    if no tasks: report idle and exit

    task = tasks[0]
    TaskUpdate(task, status=in_progress)

    if task == "R03-1: Create branch":
      # Read task metadata for SHA and branch name
      create_branch()
      TaskUpdate(task, status=completed)

    if task == "R03-2: Implement":
      implement_r03()
      TaskUpdate(task, status=completed)

    # ... continue

Lead: [Monitors TaskList for state changes]
Lead: When R03-1 completes → unblocks R03-2
Lead: When R03-2 completes → run reviews → if pass: unblock R03-3
Lead: When R03-3 completes → merge PR, update board

Lead: Broadcast: "R03 complete. Main SHA updated."
```

**Pros:**
- ✅ No resume needed (tasks drive work)
- ✅ Clear state (TaskList is source of truth)
- ✅ Natural async coordination
- ✅ Built into Claude Code

**Cons:**
- ❌ Less real-time (polling TaskList)
- ❌ Harder to pass dynamic data (SHA, branch name)
- ❌ Lead can't easily "ask" worker questions

**Loop Closure:** Task completion = closed loop. Lead verifies by checking TaskList state.

---

## 4. Recommended Architecture: **Hybrid Task-Per-Turn + TaskList**

**Combine best of Option A and Option D.**

### Design

**Lead Agent:**
- Maintains TaskList with ticket decomposition
- Spawns workers for specific tasks (not whole tickets)
- Each spawn is one discrete, completable action
- TaskList tracks progress
- Lead verifies completion after each task

**Worker Agent:**
- Receives single, clear task per spawn
- Executes task completely in one turn
- Returns result in completion message
- Exits (goes idle)

**TaskList Structure:**
```
R03-branch-creation:
  subject: Create branch for R03 from clean main
  description: |
    SHA: ac4b8f963
    Branch: claude/r03-async-reader-mode
    Workspace: /tmp/rayniyomi-workspaces/worker-a
  status: pending
  metadata:
    ticket: R03
    phase: branch-creation
    sha: ac4b8f963
    branch_name: claude/r03-async-reader-mode

R03-implementation:
  subject: Implement R03 - async ReaderViewModel
  blockedBy: [R03-branch-creation]
  description: |
    Make ReaderViewModel.setMangaReadingMode non-blocking
    Branch: claude/r03-async-reader-mode
    Workspace: /tmp/rayniyomi-workspaces/worker-a
  status: pending
  metadata:
    ticket: R03
    phase: implementation

R03-pr-creation:
  subject: Create PR for R03
  blockedBy: [R03-implementation]
  description: |
    Push branch and create PR with proper format
    Title: "refactor: make ReaderViewModel.setMangaReadingMode non-blocking (R03)"
    Include: "Closes #12"
  status: pending
  metadata:
    ticket: R03
    phase: pr-creation
```

### Workflow

**1. Lead: Decompose Ticket**
```
Lead: Break R03 into discrete tasks:
  - Branch creation (with SHA in metadata)
  - Implementation (with acceptance criteria)
  - PR creation (with format requirements)

Lead: Add to TaskList with dependencies
```

**2. Lead: Assign First Task**
```
Lead: Get next unblocked task for R03
Task: R03-branch-creation (pending)

Lead: Spawn worker for this specific task
Task subagent_type="android-expert"
     name="worker-a"
     prompt="Execute R03-branch-creation task.

     Read task details:
     TaskGet('R03-branch-creation')

     Extract metadata:
     - SHA: {task.metadata.sha}
     - Branch: {task.metadata.branch_name}
     - Workspace: {task.metadata.workspace}

     Execute:
     cd {workspace}
     git checkout -b {branch} {sha}

     Verify:
     git branch --show-current  # Should show branch name

     Report completion:
     TaskUpdate('R03-branch-creation', status='completed')

     Return message:
     'Branch created: {branch} from SHA {sha}'"

Worker: [Executes task completely]
Worker: "Branch created: claude/r03-async-reader-mode from SHA ac4b8f963"
Worker: [TaskUpdate marks complete]
Worker: [Exits]

Lead: [Receives completion message]
Lead: [Verifies via TaskList: R03-branch-creation = completed]
Lead: [Verifies via workspace: ls /tmp/.../worker-a/.git/refs/heads/]
Lead: ✅ Loop closed (task complete + verified)
```

**3. Lead: Assign Next Task**
```
Lead: Get next unblocked task for R03
Task: R03-implementation (now unblocked)

Lead: Spawn worker for implementation
Task subagent_type="android-expert"
     name="worker-a"
     prompt="Execute R03-implementation task.

     Read task details:
     TaskGet('R03-implementation')

     Workspace: /tmp/rayniyomi-workspaces/worker-a
     Branch: claude/r03-async-reader-mode (already created)

     Execute:
     1. cd {workspace}
     2. git checkout claude/r03-async-reader-mode
     3. Implement changes per acceptance criteria
     4. Run verification: ./gradlew :app:compileDebugKotlin
     5. Commit changes: git commit -m 'R03: ...'

     Report completion:
     TaskUpdate('R03-implementation', status='completed',
                metadata={commit_sha: '...', files_changed: [...]})

     Return message:
     'Implementation complete. Commit: {sha}. Files: {list}.'"

Worker: [Implements R03]
Worker: "Implementation complete. Commit: def456. Files: [ReaderViewModel.kt]"
Worker: [TaskUpdate marks complete with metadata]
Worker: [Exits]

Lead: [Receives completion]
Lead: [Verifies: git log --oneline in workspace]
Lead: [Runs reviews: conflict check + code debate]
Lead: ✅ Loop closed (implementation verified + reviewed)
```

**4. Lead: Assign PR Creation**
```
Lead: Reviews passed
Lead: Spawn worker for PR creation

Worker: [Pushes branch]
Worker: [Creates PR via gh CLI]
Worker: "PR created: https://github.com/.../pull/110"
Worker: [TaskUpdate marks complete with PR URL]
Worker: [Exits]

Lead: [Verifies PR exists: gh pr view 110]
Lead: [Merges PR]
Lead: [Updates R-BOARD]
Lead: [Updates main SHA]
Lead: [Marks R03 complete in TaskList]
Lead: ✅ Loop closed (PR merged, board updated, verified)
```

### Loop Closure Mechanism

**Every task spawn closes with:**
1. Worker completes task
2. Worker updates TaskList
3. Worker returns completion message
4. Lead receives message (auto-delivered)
5. Lead verifies TaskList state (completed)
6. Lead verifies actual result (git branch exists, PR created, etc.)
7. Lead records in audit log

**No confirmations needed because:**
- Each spawn is one complete action
- TaskList state = source of truth
- Lead verifies both TaskList AND actual state
- If task not complete, TaskList won't show completed

### Benefits

✅ **No resume needed** - Each spawn is one-shot task
✅ **Clear loop closure** - Task completion = loop closed
✅ **Easy verification** - Check TaskList + check actual state
✅ **Simple worker code** - Execute one task, report, exit
✅ **State durability** - TaskList persists across spawns
✅ **Debuggable** - TaskList shows exact progress
✅ **Parallel-ready** - Multiple workers can claim different tasks

### Token Efficiency

**Per-ticket cost:**
- Branch creation spawn: ~5k tokens
- Implementation spawn: ~60k tokens (main work)
- PR creation spawn: ~5k tokens
- **Total: ~70k tokens** (vs 87k with continuous agent)

**Actually more efficient** because:
- No polling overhead
- No idle waiting
- No resume context overhead
- Workers exit immediately after task

---

## 5. Detailed Component Design

### Lead Agent Responsibilities

```python
class LeadAgent:
    def __init__(self):
        self.current_main_sha = None
        self.active_workers = {}
        self.token_budget_used = 0
        self.audit_log = []

    def decompose_ticket(self, ticket_id):
        """Break ticket into discrete tasks with dependencies."""
        # R03 → [branch-creation, implementation, pr-creation]
        tasks = [
            Task(
                id=f"{ticket_id}-branch-creation",
                subject=f"Create branch for {ticket_id}",
                metadata={
                    "sha": self.current_main_sha,
                    "branch_name": f"claude/{ticket_id}-slug",
                    "workspace": f"/tmp/rayniyomi-workspaces/worker-a"
                }
            ),
            Task(
                id=f"{ticket_id}-implementation",
                subject=f"Implement {ticket_id}",
                blockedBy=[f"{ticket_id}-branch-creation"]
            ),
            Task(
                id=f"{ticket_id}-pr-creation",
                subject=f"Create PR for {ticket_id}",
                blockedBy=[f"{ticket_id}-implementation"]
            )
        ]
        for task in tasks:
            TaskCreate(task)

    def assign_next_task(self, worker_name):
        """Get next unblocked task and spawn worker for it."""
        task = TaskList.get(status="pending", no_blockedBy=True)

        if not task:
            return None  # No work available

        # Spawn worker for this specific task
        spawn_worker(
            name=worker_name,
            task_id=task.id,
            prompt=self.generate_task_prompt(task)
        )

        self.audit_log.append({
            "time": now(),
            "action": "TASK_ASSIGNED",
            "task": task.id,
            "worker": worker_name
        })

    def on_worker_completion(self, worker_name, message):
        """Handle worker completion message."""
        # 1. Extract task ID from message
        task_id = extract_task_id(message)

        # 2. Verify TaskList shows completed
        task = TaskGet(task_id)
        assert task.status == "completed", "Task not marked complete!"

        # 3. Verify actual result
        if "branch-creation" in task_id:
            self.verify_branch_created(task)
        elif "implementation" in task_id:
            self.verify_implementation(task)
            self.run_reviews(task)
        elif "pr-creation" in task_id:
            self.verify_pr_created(task)
            self.merge_pr(task)

        # 4. Record completion
        self.audit_log.append({
            "time": now(),
            "action": "TASK_COMPLETED",
            "task": task_id,
            "worker": worker_name
        })

        # 5. Assign next task to worker
        self.assign_next_task(worker_name)

    def verify_branch_created(self, task):
        """Verify branch actually exists in workspace."""
        workspace = task.metadata.workspace
        branch = task.metadata.branch_name

        result = bash(f"cd {workspace} && git branch --show-current")
        assert result == branch, "Branch not created!"

        # Update state
        self.active_branches[branch] = {
            "task_id": task.id,
            "created_at": now()
        }

    def verify_implementation(self, task):
        """Verify code changes exist."""
        workspace = task.metadata.workspace

        # Check commit exists
        result = bash(f"cd {workspace} && git log --oneline -1")
        assert task.id in result, "No commit for this task!"

        # Verify files changed
        files = bash(f"cd {workspace} && git diff --name-only HEAD~1")
        assert len(files) > 0, "No files changed!"

    def run_reviews(self, task):
        """Run review gates before approving merge."""
        # 1. Pre-merge conflict check
        conflict_check = bash(f"git merge-tree ...")
        assert "conflicts" not in conflict_check, "Conflicts detected!"

        # 2. Android code review debate
        review_result = invoke_skill("android-code-review-debate")
        assert review_result.passed, "Code review failed!"

    def verify_pr_created(self, task):
        """Verify PR actually exists."""
        pr_url = task.metadata.pr_url
        pr_number = extract_pr_number(pr_url)

        pr_state = bash(f"gh pr view {pr_number} --json state")
        assert pr_state == "OPEN", "PR not created!"

    def merge_pr(self, task):
        """Merge PR and update board."""
        pr_number = task.metadata.pr_number

        # Merge
        bash(f"gh pr merge {pr_number} --squash --delete-branch")

        # Verify merged
        pr_state = bash(f"gh pr view {pr_number} --json state")
        assert pr_state == "MERGED", "PR not merged!"

        # Update main SHA
        bash("git fetch ryacub")
        self.current_main_sha = bash("git rev-parse ryacub/main")

        # Update R-BOARD
        self.update_rboard(task.metadata.ticket)

        # Broadcast
        self.broadcast_all_workers(
            f"Main updated to {self.current_main_sha}. "
            f"{task.metadata.ticket} merged."
        )
```

### Worker Agent Design

```python
# Worker receives single task per spawn
def execute_task(task_id):
    """Execute one discrete task and exit."""

    # 1. Read task details from TaskList
    task = TaskGet(task_id)

    # 2. Mark task as in-progress
    TaskUpdate(task_id, status="in_progress")

    # 3. Execute based on task type
    if "branch-creation" in task_id:
        result = create_branch(
            workspace=task.metadata.workspace,
            branch=task.metadata.branch_name,
            sha=task.metadata.sha
        )

    elif "implementation" in task_id:
        result = implement_changes(
            workspace=task.metadata.workspace,
            ticket=task.metadata.ticket,
            acceptance_criteria=task.description
        )

    elif "pr-creation" in task_id:
        result = create_pr(
            workspace=task.metadata.workspace,
            branch=task.metadata.branch_name,
            ticket=task.metadata.ticket
        )

    # 4. Mark task as completed with result metadata
    TaskUpdate(
        task_id,
        status="completed",
        metadata=result  # {commit_sha, files_changed, pr_url, etc.}
    )

    # 5. Return completion message to lead
    return f"Task {task_id} completed. Result: {result}"

    # 6. Exit (no waiting, no polling, no idle)
```

---

## 6. Implementation Plan

### Phase 1: Build Task Decomposition
1. Create task decomposition logic for each ticket type
2. Add to TaskList with proper dependencies
3. Test TaskList state management

### Phase 2: Build Single-Task Worker Template
1. Create worker spawn template for each task type
2. Test worker can execute and update TaskList
3. Verify worker exits after completion

### Phase 3: Build Lead Verification Logic
1. Implement verify_branch_created()
2. Implement verify_implementation() + run_reviews()
3. Implement verify_pr_created() + merge_pr()

### Phase 4: Build Lead Assignment Loop
1. Lead monitors TaskList for completed tasks
2. Lead verifies completion
3. Lead assigns next task to idle worker

### Phase 5: Phase A Validation
1. Decompose R03 into tasks
2. Spawn worker for branch-creation
3. Verify loop closes
4. Spawn worker for implementation
5. Run reviews
6. Spawn worker for PR creation
7. Merge and verify

### Phase 6: Phase B Validation (Parallel)
1. Decompose R06 and R07 into tasks
2. Spawn worker-a for R06-branch-creation
3. Spawn worker-b for R07-branch-creation (parallel)
4. Verify both complete without conflicts
5. Continue through implementation and PR phases

---

## 7. Success Criteria

### System Works If:
1. ✅ Worker spawns complete one task and exit cleanly
2. ✅ TaskList accurately reflects state
3. ✅ Lead verifies both TaskList AND actual state
4. ✅ No manual intervention needed
5. ✅ All loops close with verification
6. ✅ Token cost < 100k per T1 ticket
7. ✅ Parallel workers don't conflict
8. ✅ Branch pollution = 0%

### System Fails If:
1. ❌ Workers stay idle waiting for resume
2. ❌ TaskList state doesn't match reality
3. ❌ Lead can't verify completion
4. ❌ Requires manual fixes mid-flow
5. ❌ Token cost > 200k per T1 ticket

---

## 8. Comparison to Original Design

### Original Design (Messaging-Based)
```
Lead ↔ Worker (continuous message exchange)
- Worker requests action
- Lead approves
- Worker confirms
- Loop closes

Issues:
- Workers go idle after messages
- Requires resume to continue
- Confirmation step often missed
- Complex state tracking
```

### New Design (Task-Based)
```
Lead → Worker (one task spawn)
- Worker executes complete task
- Worker updates TaskList
- Worker returns result
- Worker exits

Benefits:
- No resume needed
- TaskList = source of truth
- Natural completion = loop closure
- Simple state tracking
```

---

## Next Steps

1. **Pause current Phase A** - Don't patch the messaging-based approach
2. **Implement task decomposition** - Break R03 into tasks
3. **Build single-task worker** - Test one branch-creation task
4. **Verify loop closes** - Ensure TaskList + actual state match
5. **Iterate until reliable** - Fix issues in design, not in patches
6. **Resume Phase A with new architecture** - Restart validation properly

**Let's build this right, not fast.**
