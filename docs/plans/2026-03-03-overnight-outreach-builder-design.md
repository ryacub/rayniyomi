# Overnight Parallel Outreach Builder — Design

**Date:** 2026-03-03
**Status:** Approved

## Problem

NeonCube Automations is at 56/100 emails sent with $0 revenue. The outreach pipeline is manually triggered and limited to what Tessa can do in a single session. The overnight hours (2am–7am PST) are idle time that could be used to research prospects and draft personalized emails at scale.

## Goal

Use OpenClaw's `sessions_spawn`/`sessions_send` to run a nightly parallel worker swarm that:
1. Pulls leads from the AUTONOMOUS.md backlog
2. Spawns 3–5 worker sub-sessions simultaneously
3. Has each worker research a batch of 3 leads and write personalized email drafts
4. Queues drafts for the existing morning_batch.py pipeline to send at 9am PST

## Architecture

```
[2:00am PST cron — neoncube-overnight-orchestrator]
     │
     ▼
[Orchestrator Session — Tessa]
  Reads AUTONOMOUS.md → picks 10–15 leads from backlog
  Splits into 3–5 batches of 3 leads each
  sessions_spawn × N → one Worker session per batch
     │
     ├──▶ [Worker A] batch 1
     ├──▶ [Worker B] batch 2
     └──▶ [Worker C] batch 3
          │
          For each lead in batch:
          1. Brave Search → research company/person
          2. humanizer skill → personalize angle
          3. email-personalizer skill → draft email body
          4. Write draft to temp file (queue-worker-X.json)
          5. Append status line to memory/tasks-log.md
     │
     ▼
[Orchestrator merges temp files → memory/outreach-queue.json]
     │
     ▼
[9:00am PST cron — neoncube-morning-batch (existing)]
  Reads memory/outreach-queue.json
  Sends via Gmail
  Updates Google Sheet lead DB
  Archives sent entries to memory/sent-archive.json
```

## Components

### AUTONOMOUS.md (orchestrator-only writes)

Existing file. Gains a new `## Lead Backlog` section:

```markdown
## Lead Backlog
<!-- Orchestrator reads this nightly. Do not edit manually during active sessions. -->
- name: John Smith | company: CoachCo | email: john@coachco.com | linkedin: ...
- name: Jane Doe | company: GrowthLab | email: jane@growthlab.com | linkedin: ...
```

Orchestrator claims N leads at session start (marks with `[claimed YYYY-MM-DD]`), moves processed leads to a `## Processed` section when workers complete.

### memory/tasks-log.md (workers append-only)

Append-only log. Workers write one line per completed lead:

```
[2026-03-04T10:23:41Z] Worker-A: researched John Smith @ CoachCo → draft written → queue-worker-a.json
[2026-03-04T10:31:07Z] Worker-B: researched Jane Doe @ GrowthLab → draft written → queue-worker-b.json
```

Orchestrator reads this to confirm all workers finished before merging queues.

### memory/outreach-queue.json (merged by orchestrator)

```json
[
  {
    "lead_id": "john-smith-coachco",
    "to": "john@coachco.com",
    "subject": "Automating your client onboarding — 15 minutes?",
    "body": "...",
    "status": "pending",
    "queued_at": "2026-03-04T10:45:00Z"
  }
]
```

morning_batch.py reads this, sends each `pending` entry, updates `status` to `sent`.

### memory/sent-archive.json

Append-only archive of sent emails. morning_batch.py moves `sent` entries here after sending to keep the queue file small.

## Cron Changes

Add one new job to `/home/node/.openclaw/cron/jobs.json`:

```json
{
  "id": "neoncube-overnight-orchestrator",
  "schedule": "0 10 * * *",
  "description": "Nightly parallel outreach builder — 2:00am PST",
  "prompt": "Run the overnight outreach builder: read AUTONOMOUS.md lead backlog, spawn worker sessions to research and draft emails for tonight's batch, merge results into memory/outreach-queue.json"
}
```

Existing `neoncube-morning-batch` at `0 17 * * *` (9:00am PST) is unchanged.

## Config Change

Bump `maxSpawnDepth` from 1 → 2 so worker sub-sessions can use tools (Brave Search, email skills):

```json
// In OpenClaw config / docker-compose.yml env section:
agents.defaults.subagents.maxSpawnDepth = 2
```

This is the only infrastructure change required.

## Error Handling

| Scenario | Behavior |
|----------|----------|
| Worker fails mid-batch | Only affects that worker's 3 leads. Other workers complete. Failed leads stay in backlog for next night. |
| Concurrent queue writes | Workers write to separate temp files (`queue-worker-A.json`). Orchestrator merges after all workers done. No write conflicts. |
| Empty backlog | Orchestrator detects empty backlog, skips spawn, sends Telegram: "Backlog empty — add leads to AUTONOMOUS.md" |
| maxSpawnDepth not set | Orchestrator validates `sessions_spawn` availability before spawning. Falls back to serial single-session mode. |
| Orchestrator times out | Workers are independent — any already-running workers continue to completion. Queue is partial but valid. |

## Success Metrics

- **Throughput:** 10–15 personalized email drafts queued per night (vs. ~2–3 per manual session)
- **Queue health:** morning_batch.py should find ≥10 `pending` entries each morning
- **Error rate:** <1 failed worker per night (lead stays in backlog, auto-retried next night)

## Files Changed

| File | Change |
|------|--------|
| `/home/node/.openclaw/cron/jobs.json` | Add `neoncube-overnight-orchestrator` job |
| `/home/node/.openclaw/workspace/AUTONOMOUS.md` | Add `## Lead Backlog` section |
| `/home/node/.openclaw/workspace/memory/outreach-queue.json` | Create (new file) |
| `/home/node/.openclaw/workspace/memory/sent-archive.json` | Create (new file) |
| OpenClaw config | Bump `maxSpawnDepth` to 2 |
| `morning_batch.py` (existing script) | Minor update: read from `outreach-queue.json`, archive to `sent-archive.json` |
