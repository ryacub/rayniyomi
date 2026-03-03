# Overnight Parallel Outreach Builder — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Run a nightly parallel worker swarm on OpenClaw that researches 10–15 prospects and writes personalized email drafts overnight, queued for the morning send pipeline.

**Architecture:** A cron job fires at 2am PST, triggering the main Tessa session to read AUTONOMOUS.md's lead backlog, spawn 3–5 worker sub-sessions simultaneously (one per batch of 3 leads), have each worker research via Brave Search + draft via humanizer/email-personalizer skills, then merge all drafts into `memory/outreach-queue.json` for a lightweight sender script to consume at 9am.

**Tech Stack:** OpenClaw gateway (Docker, VPS 5.75.164.180 port 2222), openclaw.json config (JSON), cron/jobs.json (OpenClaw cron), Python 3 scripts (VPS-side inside container volume), Gmail API (existing shared_utils.py integration).

---

## Pre-flight: Connect to VPS

All steps run over SSH. The working directory is inside the Docker container via `docker exec`.

```bash
# SSH alias for all steps:
ssh -p 2222 ray-openclaw@5.75.164.180

# Exec into container (read/write files on the openclaw-config volume):
docker exec -it openclaw-openclaw-gateway-1 bash
```

The `openclaw-config` and `openclaw-workspace` volumes persist across container restarts — edits survive `docker compose restart`.

---

## Task 1: Bump maxSpawnDepth to 2

**Why:** `DEFAULT_SUBAGENT_MAX_SPAWN_DEPTH = 1` means worker sub-sessions can't use `sessions_spawn` themselves, but it also means workers can't use tools that require depth-gated permissions (Brave Search is allowed at depth 1 — this config controls how deep the tree can go). Current config has `maxConcurrent: 8` but no `maxSpawnDepth` key (defaults to 1). We need depth 2 so orchestrator (depth 0) → workers (depth 1) → tools (allowed at depth ≤ maxSpawnDepth).

**File:** `/home/node/.openclaw/openclaw.json` (inside container, on `openclaw-config` volume)

**Step 1: Read current config**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 \
  'docker exec openclaw-openclaw-gateway-1 cat /home/node/.openclaw/openclaw.json | python3 -m json.tool | grep -A5 "subagents"'
```

Expected output:
```
"subagents": {
    "maxConcurrent": 8
}
```

**Step 2: Add maxSpawnDepth**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 'docker exec openclaw-openclaw-gateway-1 python3 -c "
import json, pathlib
p = pathlib.Path(\"/home/node/.openclaw/openclaw.json\")
cfg = json.loads(p.read_text())
cfg[\"agents\"][\"defaults\"][\"subagents\"][\"maxSpawnDepth\"] = 2
p.write_text(json.dumps(cfg, indent=2))
print(\"Done. subagents:\", cfg[\"agents\"][\"defaults\"][\"subagents\"])
"'
```

Expected output:
```
Done. subagents: {'maxConcurrent': 8, 'maxSpawnDepth': 2}
```

**Step 3: Verify**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 \
  'docker exec openclaw-openclaw-gateway-1 cat /home/node/.openclaw/openclaw.json | python3 -m json.tool | grep -A6 "subagents"'
```

Expected: `"maxSpawnDepth": 2` present.

**Step 4: Restart gateway to apply config**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 \
  'cd /opt/openclaw && docker compose restart openclaw-gateway'
```

Wait ~90 seconds for port 18789 to come back:
```bash
ssh -p 2222 ray-openclaw@5.75.164.180 \
  'for i in $(seq 1 12); do ss -tlnp | grep -q 18789 && echo "UP" && break || (echo "waiting... $i"; sleep 10); done'
```

---

## Task 2: Create outreach-queue.json and sent-archive.json scaffolds

**Why:** These files need to exist before the overnight orchestrator or sender script runs. Empty JSON arrays are valid initial state.

**Files:**
- Create: `/home/node/.openclaw/workspace/memory/outreach-queue.json`
- Create: `/home/node/.openclaw/workspace/memory/sent-archive.json`

**Step 1: Create both files**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 'docker exec openclaw-openclaw-gateway-1 bash -c "
echo \"[]\" > /home/node/.openclaw/workspace/memory/outreach-queue.json
echo \"[]\" > /home/node/.openclaw/workspace/memory/sent-archive.json
echo \"Created:\"
ls -lh /home/node/.openclaw/workspace/memory/outreach-queue.json
ls -lh /home/node/.openclaw/workspace/memory/sent-archive.json
"'
```

Expected: Both files created, 3 bytes each.

---

## Task 3: Create AUTONOMOUS.md with Lead Backlog section

**Why:** The overnight orchestrator reads this file to know who to research. It must have a `## Lead Backlog` section with the structured lead list. The orchestrator marks leads as `[claimed YYYY-MM-DD]` when it picks them up for the night, and `[processed YYYY-MM-DD]` when the worker finishes.

**File:** `/home/node/.openclaw/workspace/AUTONOMOUS.md`

**Step 1: Check if file exists already**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 \
  'docker exec openclaw-openclaw-gateway-1 cat /home/node/.openclaw/workspace/AUTONOMOUS.md 2>/dev/null | head -20 || echo "DOES NOT EXIST"'
```

**Step 2: Create AUTONOMOUS.md**

If the file exists, append the Lead Backlog section to it. If it doesn't exist, create it:

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 'docker exec openclaw-openclaw-gateway-1 bash -c "cat > /home/node/.openclaw/workspace/AUTONOMOUS.md << '"'"'HEREDOC'"'"'
# NeonCube Automations — Autonomous Operations

## Goals

- **Primary:** Close 2 clients at \$997 each (\$1,994 total) before the deadline
- **Offer:** Custom n8n workflow automation (lead follow-up, intake, or booking automation)
- **Target:** Business coaches (life, business, fitness) drowning in admin
- **Email:** neoncubeautomations@gmail.com
- **Calendar:** https://cal.com/neoncubeautomations/15min

## Current Status

- Emails sent: 56/100
- Revenue: \$0
- Mode: Survival — need first client ASAP

## Overnight Outreach Instructions

Each night, the orchestrator (this session) should:
1. Read the ## Lead Backlog below
2. Select up to 15 unclaimed leads
3. Mark each selected lead as [claimed YYYY-MM-DD]
4. Split into batches of 3 leads each
5. Spawn one worker sub-session per batch using sessions_spawn
6. Each worker: research lead with Brave Search, draft email with humanizer + email-personalizer skill, write result to memory/queue-worker-{id}.json, append done line to memory/tasks-log.md
7. After all workers finish (check tasks-log.md), merge all queue-worker-*.json files into memory/outreach-queue.json
8. Mark each processed lead as [processed YYYY-MM-DD] in this file
9. Send Telegram notification: \"Overnight batch complete: N drafts queued\"

### Worker Prompt Template

When spawning each worker, use this prompt (substitute actual lead data):

---
You are a NeonCube outreach researcher. Your job is to research the following leads and write personalized cold email drafts.

For each lead:
1. Use Brave Search to find: their business name, recent content/posts, what they coach on, any pain points visible publicly
2. Use the humanizer skill to identify the most personal angle for NeonCube's offer (\$997 custom n8n automation, 5-hour freedom guarantee, business coaches)
3. Write a personalized cold email (max 150 words, Day-0 curiosity framework) using the email-personalizer skill
4. Write the draft to memory/queue-worker-{WORKER_ID}.json in this format:
   [{\"lead_id\": \"firstname-lastname-company\", \"to\": \"email@domain.com\", \"subject\": \"...\", \"body\": \"...\", \"status\": \"pending\", \"queued_at\": \"ISO-8601-timestamp\"}]
5. Append one line to memory/tasks-log.md:
   [TIMESTAMP] Worker-{WORKER_ID}: researched FIRSTNAME LASTNAME @ COMPANY → draft written

Your leads:
{LEAD_LIST}
---

## Lead Backlog

<!-- Format: - name: FULL NAME | company: COMPANY | email: EMAIL | niche: NICHE | notes: NOTES -->
<!-- Orchestrator marks: [claimed YYYY-MM-DD] when picked up, [processed YYYY-MM-DD] when done -->
<!-- Add new leads here. Do not edit lines marked [claimed] or [processed] during active sessions. -->

## Processed Leads

<!-- Leads that have been researched and drafted. Moved here by the orchestrator after workers complete. -->
HEREDOC
echo \"Created AUTONOMOUS.md\"
"'
```

**Step 3: Verify**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 \
  'docker exec openclaw-openclaw-gateway-1 head -20 /home/node/.openclaw/workspace/AUTONOMOUS.md'
```

Expected: File starts with `# NeonCube Automations — Autonomous Operations`.

**Step 4: Populate Lead Backlog with initial leads**

Pull leads from the Google Sheet and add them to the backlog. Run this from inside the container:

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 'docker exec openclaw-openclaw-gateway-1 python3 -c "
import sys
sys.path.insert(0, \"/home/node/.openclaw/workspace/skills/email-personalizer/scripts\")
from shared_utils import get_access_token
import urllib.request, json, pathlib

token = get_access_token()
SHEET_ID = \"1P2Av8qoojFjoD3uPqfd4M2QfXj3H5xu_u6KTYk4reBs\"
url = f\"https://sheets.googleapis.com/v4/spreadsheets/{SHEET_ID}/values/All%20Leads?key=\"
req = urllib.request.Request(
    f\"https://sheets.googleapis.com/v4/spreadsheets/{SHEET_ID}/values/All%20Leads\",
    headers={\"Authorization\": f\"Bearer {token}\"}
)
with urllib.request.urlopen(req) as r:
    data = json.loads(r.read())

rows = data.get(\"values\", [])
header = rows[0] if rows else []
print(\"Sheet columns:\", header[:10])
print(\"Total rows:\", len(rows) - 1)
# Show first 5 data rows
for row in rows[1:6]:
    print(row)
"'
```

Expected: prints column headers and first 5 leads. Use this output to understand the sheet structure before populating AUTONOMOUS.md.

Once you know the column indices for Name, Email, Company, Niche, Status — add 15–20 `New` status leads to the `## Lead Backlog` section manually:

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 'docker exec openclaw-openclaw-gateway-1 python3 - << '"'"'PYEOF'"'"'
import sys, json, pathlib, urllib.request
sys.path.insert(0, "/home/node/.openclaw/workspace/skills/email-personalizer/scripts")
from shared_utils import get_access_token

token = get_access_token()
SHEET_ID = "1P2Av8qoojFjoD3uPqfd4M2QfXj3H5xu_u6KTYk4reBs"
req = urllib.request.Request(
    f"https://sheets.googleapis.com/v4/spreadsheets/{SHEET_ID}/values/All%20Leads",
    headers={"Authorization": f"Bearer {token}"}
)
with urllib.request.urlopen(req) as r:
    data = json.loads(r.read())

rows = data.get("values", [])
header = rows[0]

# Adjust these indices after checking the header output above
NAME_COL = header.index("Name") if "Name" in header else 0
EMAIL_COL = header.index("Email") if "Email" in header else 1
COMPANY_COL = header.index("Company") if "Company" in header else 2
NICHE_COL = header.index("Niche") if "Niche" in header else 3
STATUS_COL = header.index("Status") if "Status" in header else 4

def safe_get(row, idx, default=""):
    return row[idx].strip() if idx < len(row) else default

new_leads = [
    row for row in rows[1:]
    if safe_get(row, STATUS_COL).strip() == "New"
    and safe_get(row, EMAIL_COL)
][:20]  # take first 20 New leads

backlog_lines = []
for row in new_leads:
    name = safe_get(row, NAME_COL)
    email = safe_get(row, EMAIL_COL)
    company = safe_get(row, COMPANY_COL)
    niche = safe_get(row, NICHE_COL)
    backlog_lines.append(f"- name: {name} | company: {company} | email: {email} | niche: {niche} | notes:")

# Append to AUTONOMOUS.md before ## Processed Leads
p = pathlib.Path("/home/node/.openclaw/workspace/AUTONOMOUS.md")
content = p.read_text()
insertion_marker = "## Processed Leads"
leads_block = "\n".join(backlog_lines) + "\n\n"
content = content.replace(insertion_marker, leads_block + insertion_marker)
p.write_text(content)
print(f"Added {len(backlog_lines)} leads to backlog")
PYEOF'
```

Expected: `Added N leads to backlog` (aim for 15–20).

**Step 5: Verify backlog populated**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 \
  'docker exec openclaw-openclaw-gateway-1 grep -c "^- name:" /home/node/.openclaw/workspace/AUTONOMOUS.md'
```

Expected: number matching leads added.

---

## Task 4: Create send_overnight_queue.py

**Why:** A lightweight script that reads `memory/outreach-queue.json`, sends pending emails via Gmail, and archives sent ones to `memory/sent-archive.json`. Intentionally separate from `morning_batch.py` (no changes to existing scripts needed).

**File:** Create `/home/node/.openclaw/workspace/skills/email-personalizer/scripts/send_overnight_queue.py`

**Step 1: Create the script**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 'docker exec openclaw-openclaw-gateway-1 bash -c "cat > /home/node/.openclaw/workspace/skills/email-personalizer/scripts/send_overnight_queue.py << '"'"'PYEOF'"'"'
#!/usr/bin/env python3
"""
Send emails from overnight outreach queue.

Reads memory/outreach-queue.json, sends pending entries via Gmail,
archives sent ones to memory/sent-archive.json.

Usage:
  python3 send_overnight_queue.py
  python3 send_overnight_queue.py --dry-run
  python3 send_overnight_queue.py --count 5
"""
import argparse, json, sys, time, random
from pathlib import Path
from datetime import datetime, timezone

SCRIPTS_DIR = Path(__file__).parent
sys.path.insert(0, str(SCRIPTS_DIR))
from shared_utils import get_access_token
from send_batch import gmail_send, increment_daily_count, get_daily_count, DAILY_CAP, SEND_DELAY_MIN, SEND_DELAY_MAX, UNSUBSCRIBE_FOOTER

WORKSPACE = Path("/home/node/.openclaw/workspace")
QUEUE_FILE = WORKSPACE / "memory" / "outreach-queue.json"
ARCHIVE_FILE = WORKSPACE / "memory" / "sent-archive.json"


def load_json(path: Path, default):
    if not path.exists():
        return default
    try:
        return json.loads(path.read_text())
    except Exception:
        return default


def save_json(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--count", type=int, default=None, help="Max emails to send")
    args = parser.parse_args()

    queue = load_json(QUEUE_FILE, [])
    archive = load_json(ARCHIVE_FILE, [])

    pending = [e for e in queue if e.get("status") == "pending"]
    if not pending:
        print("No pending entries in outreach-queue.json")
        return

    daily_sent = get_daily_count()
    remaining_cap = DAILY_CAP - daily_sent
    if remaining_cap <= 0:
        print(f"Daily cap reached ({DAILY_CAP}). Skipping.")
        return

    to_send = pending[:min(len(pending), remaining_cap)]
    if args.count:
        to_send = to_send[:args.count]

    print(f"Pending: {len(pending)} | Daily cap remaining: {remaining_cap} | Sending: {len(to_send)}")

    sent_count = 0
    for entry in to_send:
        to = entry.get("to", "")
        subject = entry.get("subject", "")
        body = entry.get("body", "") + "\n\n" + UNSUBSCRIBE_FOOTER

        print(f"  {'[DRY RUN] ' if args.dry_run else ''}→ {to}: {subject}")

        if not args.dry_run:
            try:
                token = get_access_token()
                gmail_send(token, "neoncubeautomations@gmail.com", to, subject, body)
                entry["status"] = "sent"
                entry["sent_at"] = datetime.now(timezone.utc).isoformat()
                archive.append(entry)
                increment_daily_count()
                sent_count += 1
                delay = random.uniform(SEND_DELAY_MIN, SEND_DELAY_MAX)
                print(f"    Sent. Waiting {delay:.0f}s...")
                time.sleep(delay)
            except Exception as e:
                print(f"    ERROR sending to {to}: {e}", file=sys.stderr)
                entry["status"] = "error"
                entry["error"] = str(e)
        else:
            sent_count += 1

    # Write back queue (remove sent/error entries), update archive
    if not args.dry_run:
        remaining_queue = [e for e in queue if e.get("status") == "pending"]
        save_json(QUEUE_FILE, remaining_queue)
        save_json(ARCHIVE_FILE, archive)

    print(f"Done. Sent: {sent_count}")


if __name__ == "__main__":
    main()
PYEOF
chmod +x /home/node/.openclaw/workspace/skills/email-personalizer/scripts/send_overnight_queue.py
echo Created"'
```

Expected: `Created`

**Step 2: Dry-run test with empty queue**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 \
  'docker exec openclaw-openclaw-gateway-1 python3 /home/node/.openclaw/workspace/skills/email-personalizer/scripts/send_overnight_queue.py --dry-run'
```

Expected: `No pending entries in outreach-queue.json`

**Step 3: Dry-run test with a fake entry**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 'docker exec openclaw-openclaw-gateway-1 python3 -c "
import json
from pathlib import Path
q = Path(\"/home/node/.openclaw/workspace/memory/outreach-queue.json\")
q.write_text(json.dumps([{
    \"lead_id\": \"test-lead\",
    \"to\": \"test@example.com\",
    \"subject\": \"Test subject\",
    \"body\": \"Test body\",
    \"status\": \"pending\",
    \"queued_at\": \"2026-03-03T10:00:00Z\"
}]))
print(\"Fake entry written\")
"'

ssh -p 2222 ray-openclaw@5.75.164.180 \
  'docker exec openclaw-openclaw-gateway-1 python3 /home/node/.openclaw/workspace/skills/email-personalizer/scripts/send_overnight_queue.py --dry-run'
```

Expected output: `Pending: 1 | ... | Sending: 1` then `[DRY RUN] → test@example.com: Test subject`

**Step 4: Reset queue to empty**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 \
  'docker exec openclaw-openclaw-gateway-1 bash -c "echo [] > /home/node/.openclaw/workspace/memory/outreach-queue.json"'
```

---

## Task 5: Add overnight orchestrator cron job

**Why:** This is the trigger that wakes Tessa at 2:00am PST (10:00 UTC) and tells her to run the overnight outreach builder workflow.

**File:** `/home/node/.openclaw/cron/jobs.json`

**Step 1: Read current cron jobs**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 \
  'docker exec openclaw-openclaw-gateway-1 cat /home/node/.openclaw/cron/jobs.json | python3 -m json.tool | grep -E "\"id\"|\"expr\""'
```

Expected: shows existing 5 job IDs and schedules.

**Step 2: Add overnight orchestrator job**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 'docker exec openclaw-openclaw-gateway-1 python3 -c "
import json, pathlib

p = pathlib.Path(\"/home/node/.openclaw/cron/jobs.json\")
cron = json.loads(p.read_text())

new_job = {
    \"id\": \"neoncube-overnight-orchestrator\",
    \"name\": \"NeonCube Overnight Outreach Builder\",
    \"enabled\": True,
    \"schedule\": {
        \"kind\": \"cron\",
        \"expr\": \"0 10 * * *\"
    },
    \"state\": {},
    \"wakeMode\": \"now\",
    \"payload\": {
        \"kind\": \"systemEvent\",
        \"text\": \"OVERNIGHT OUTREACH BUILD: Read /home/node/.openclaw/workspace/AUTONOMOUS.md Lead Backlog section. Select up to 15 unclaimed leads (lines NOT marked [claimed] or [processed]). Mark each selected lead as [claimed $(date +%Y-%m-%d)] inline. Split into batches of 3. For each batch, use sessions_spawn to spawn a worker sub-session with the Worker Prompt Template from AUTONOMOUS.md (substitute actual lead data). After spawning all workers, wait by checking memory/tasks-log.md every 2 minutes until all workers have appended their completion lines. Then merge all memory/queue-worker-*.json files into memory/outreach-queue.json (append to existing array). Mark each worker's leads as [processed $(date +%Y-%m-%d)] in AUTONOMOUS.md. Delete the worker temp files. Send Telegram message: Overnight batch complete: N drafts queued in outreach-queue.json\"
    },
    \"sessionTarget\": \"main\"
}

# Check not already added
if any(j[\"id\"] == \"neoncube-overnight-orchestrator\" for j in cron[\"jobs\"]):
    print(\"Already exists — skipping\")
else:
    cron[\"jobs\"].append(new_job)
    p.write_text(json.dumps(cron, indent=2))
    print(\"Added neoncube-overnight-orchestrator job\")
"'
```

Expected: `Added neoncube-overnight-orchestrator job`

**Step 3: Add morning queue sender job (runs at 9:05am PST, after morning_batch)**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 'docker exec openclaw-openclaw-gateway-1 python3 -c "
import json, pathlib

p = pathlib.Path(\"/home/node/.openclaw/cron/jobs.json\")
cron = json.loads(p.read_text())

new_job = {
    \"id\": \"neoncube-overnight-queue-sender\",
    \"name\": \"NeonCube Overnight Queue Sender\",
    \"enabled\": True,
    \"schedule\": {
        \"kind\": \"cron\",
        \"expr\": \"5 17 * * *\"
    },
    \"state\": {},
    \"wakeMode\": \"now\",
    \"payload\": {
        \"kind\": \"systemEvent\",
        \"text\": \"cd /home/node/.openclaw/workspace && python3 skills/email-personalizer/scripts/send_overnight_queue.py --count 10 >> /tmp/overnight-queue-sender.log 2>&1\"
    },
    \"sessionTarget\": \"main\"
}

if any(j[\"id\"] == \"neoncube-overnight-queue-sender\" for j in cron[\"jobs\"]):
    print(\"Already exists — skipping\")
else:
    cron[\"jobs\"].append(new_job)
    p.write_text(json.dumps(cron, indent=2))
    print(\"Added neoncube-overnight-queue-sender job\")
"'
```

**Step 4: Verify both jobs added**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 \
  'docker exec openclaw-openclaw-gateway-1 cat /home/node/.openclaw/cron/jobs.json | python3 -m json.tool | grep "\"id\""'
```

Expected: 7 job IDs, including `neoncube-overnight-orchestrator` and `neoncube-overnight-queue-sender`.

---

## Task 6: End-to-End Dry Run

**Why:** Verify the whole pipeline works before the first real overnight run. Simulate the orchestrator's merge step manually.

**Step 1: Write two fake worker queue files**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 'docker exec openclaw-openclaw-gateway-1 python3 -c "
import json
from pathlib import Path
from datetime import datetime, timezone

mem = Path(\"/home/node/.openclaw/workspace/memory\")
ts = datetime.now(timezone.utc).isoformat()

(mem / \"queue-worker-a.json\").write_text(json.dumps([
    {\"lead_id\": \"test-alice\", \"to\": \"alice@example.com\", \"subject\": \"Save 5 hours\", \"body\": \"Hi Alice...\", \"status\": \"pending\", \"queued_at\": ts}
]))
(mem / \"queue-worker-b.json\").write_text(json.dumps([
    {\"lead_id\": \"test-bob\", \"to\": \"bob@example.com\", \"subject\": \"Save 5 hours\", \"body\": \"Hi Bob...\", \"status\": \"pending\", \"queued_at\": ts}
]))
print(\"Fake worker files created\")
"'
```

**Step 2: Run the merge (simulates what orchestrator does)**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 'docker exec openclaw-openclaw-gateway-1 python3 -c "
import json, glob
from pathlib import Path

mem = Path(\"/home/node/.openclaw/workspace/memory\")
queue_file = mem / \"outreach-queue.json\"

existing = json.loads(queue_file.read_text()) if queue_file.exists() else []
worker_files = sorted(mem.glob(\"queue-worker-*.json\"))

merged = existing[:]
for wf in worker_files:
    entries = json.loads(wf.read_text())
    merged.extend(entries)
    wf.unlink()  # clean up temp file
    print(f\"Merged {len(entries)} entries from {wf.name}\")

queue_file.write_text(json.dumps(merged, indent=2))
print(f\"outreach-queue.json now has {len(merged)} entries\")
"'
```

Expected: `Merged 1 entries from queue-worker-a.json`, `Merged 1 entries from queue-worker-b.json`, `outreach-queue.json now has 2 entries`

**Step 3: Dry-run the sender**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 \
  'docker exec openclaw-openclaw-gateway-1 python3 /home/node/.openclaw/workspace/skills/email-personalizer/scripts/send_overnight_queue.py --dry-run'
```

Expected: shows 2 pending entries, prints both as `[DRY RUN]` sends, no actual email sent.

**Step 4: Reset queue to empty**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 \
  'docker exec openclaw-openclaw-gateway-1 bash -c "echo [] > /home/node/.openclaw/workspace/memory/outreach-queue.json"'
```

---

## Task 7: Verify Cron Schedule and Commit Notes

**Step 1: Check next run time for overnight orchestrator**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 'docker exec openclaw-openclaw-gateway-1 python3 -c "
from datetime import datetime, timezone
import json
from pathlib import Path

jobs = json.loads(Path(\"/home/node/.openclaw/cron/jobs.json\").read_text())[\"jobs\"]
for j in jobs:
    if \"neoncube\" in j[\"id\"]:
        state = j.get(\"state\", {})
        next_run_ms = state.get(\"nextRunAtMs\")
        if next_run_ms:
            dt = datetime.fromtimestamp(next_run_ms / 1000, tz=timezone.utc)
            print(f\"{j[\"id\"]}: next run {dt.strftime(\"%Y-%m-%d %H:%M UTC\")}\")
        else:
            print(f\"{j[\"id\"]}: schedule {j[\"schedule\"][\"expr\"]} (no nextRunAtMs yet — will populate after first trigger)\")
"'
```

**Step 2: Confirm maxSpawnDepth is still set**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 \
  'docker exec openclaw-openclaw-gateway-1 python3 -c "import json,pathlib; c=json.loads(pathlib.Path(\"/home/node/.openclaw/openclaw.json\").read_text()); print(c[\"agents\"][\"defaults\"][\"subagents\"])"'
```

Expected: `{'maxConcurrent': 8, 'maxSpawnDepth': 2}`

**Step 3: Note in tasks-log.md that system is ready**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 'docker exec openclaw-openclaw-gateway-1 python3 -c "
from pathlib import Path
from datetime import datetime, timezone
ts = datetime.now(timezone.utc).strftime(\"%Y-%m-%dT%H:%M:%SZ\")
log = Path(\"/home/node/.openclaw/workspace/memory/tasks-log.md\")
log.parent.mkdir(parents=True, exist_ok=True)
with open(log, \"a\") as f:
    f.write(f\"[{ts}] System: overnight parallel outreach builder installed — maxSpawnDepth=2, cron at 0 10 * * *, send_overnight_queue.py ready\\n\")
print(\"Logged\")
"'
```

---

## Summary

After completing all tasks, the overnight system is live:

| Time (PST) | Event |
|---|---|
| 2:00am | `neoncube-overnight-orchestrator` cron fires → Tessa reads AUTONOMOUS.md, spawns workers |
| 2:00–5:00am | Worker sub-sessions research leads, write drafts to queue-worker-*.json + tasks-log.md |
| ~5:00am | Orchestrator merges queue files → outreach-queue.json, notifies via Telegram |
| 9:00am | `neoncube-morning-batch` fires (unchanged — handles follow-ups from Sheets) |
| 9:05am | `neoncube-overnight-queue-sender` fires → sends overnight drafts via Gmail |

To add leads for the next night, append lines to the `## Lead Backlog` section of `AUTONOMOUS.md` on the VPS. The orchestrator picks up unclaimed lines automatically.
