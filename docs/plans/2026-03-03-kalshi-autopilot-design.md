# Kalshi Autopilot — Design

**Date:** 2026-03-03
**Status:** Approved

---

## Overview

Automated prediction market trading on Kalshi using a hybrid OpenClaw + Python architecture. OpenClaw (Claude) acts as the brain — analyzing markets and making decisions every 15 minutes. Python scripts act as the I/O layer — fetching data, enforcing risk limits, and executing trades.

Kalshi is CFTC-regulated, USD-native, and provides an official demo environment at demo.kalshi.com. No crypto wallet required.

---

## Architecture

```
OpenClaw cron (*/15 * * * *)
    → Claude orchestrates
        → fetch_markets.py    writes → memory/kalshi-markets-cache.json
        → risk_check.py       reads  → memory/kalshi-portfolio.json
        → execute_trade.py    places orders via kalshi-python SDK
        → report.py           sends  → Telegram notification
```

**Scripts location:** `/home/node/.openclaw/workspace/skills/kalshi/`
**State location:** `/home/node/.openclaw/workspace/memory/`
**Cron config:** `/home/node/.openclaw/cron/jobs.json`

---

## Scripts

### fetch_markets.py
- Calls Kalshi REST API to fetch active markets
- Filters to markets with >50 YES contracts, <30 days to resolution
- Computes 24h price delta for each market
- Outputs `memory/kalshi-markets-cache.json`

### risk_check.py
- Reads `memory/kalshi-portfolio.json`
- Returns `{ "ok": true/false, "reason": "..." }` — Claude reads this before deciding to trade
- Enforces hard limits (see Risk Controls below)
- Claude cannot override a `false` response

### execute_trade.py
- Accepts CLI args: `--ticker KXBTC-25DEC31 --side yes|no --contracts N`
- Signs and submits order via `kalshi-python` SDK
- Writes trade record to `memory/kalshi-trades.json`
- Idempotent: checks for duplicate pending orders before submitting

### report.py
- Sends Telegram message summarizing the cycle
- Format: markets scanned, positions checked, trades placed, portfolio P&L

---

## Authentication

Kalshi uses RSA private key signing. The `kalshi-python` SDK handles request signing.

```python
import os
from kalshi_python import ApiClient, Configuration

config = Configuration()
config.host = "https://demo.kalshi.co/trade-api/v2"  # or trading-api.kalshi.com
# SDK reads KALSHI_API_KEY_ID + KALSHI_PRIVATE_KEY from env
```

**Credentials in `/run/secrets/openclaw/`:**
- `KALSHI_API_KEY_ID` — key ID string
- `KALSHI_PRIVATE_KEY` — RSA PEM private key
- `KALSHI_ENV` — `demo` or `live` (controls base URL)

**Environment switch:** changing `KALSHI_ENV=demo` → `KALSHI_ENV=live` is the only change needed to go live.

---

## Trading Strategies

### TAIL
- **Signal:** Market probability >90%, resolves within 7 days
- **Action:** Buy the NO side (underpriced tail risk)
- **Logic:** Crowd overconfidence near resolution misprices small tail risk

### BONDING
- **Signal:** Price swung >10% in last 24h, >14 days to resolution
- **Action:** Fade the direction of the swing (buy the opposite side)
- **Logic:** Recency bias causes overreaction; price mean-reverts over longer horizon

---

## Risk Controls

All enforced in Python. Claude cannot override.

| Control | Limit |
|---|---|
| Max position size | 5% of portfolio balance |
| Hard order cap | $10 per order |
| Max open positions | 5 |
| Daily loss halt | 15% of starting day balance |
| Min confidence | 65% (Claude self-reports before execute_trade.py runs) |

`risk_check.py` returns `{ "ok": false }` if any limit is breached. Claude reads this and skips trading for the cycle.

---

## State Files

### memory/kalshi-portfolio.json
```json
{
  "balance_usd": 150.00,
  "day_start_balance": 150.00,
  "open_positions": [],
  "last_updated": "2026-03-03T10:00:00Z"
}
```

### memory/kalshi-trades.json
```json
[
  {
    "id": "uuid",
    "ticker": "KXBTC-25DEC31",
    "side": "no",
    "contracts": 3,
    "price_cents": 8,
    "strategy": "TAIL",
    "placed_at": "2026-03-03T10:00:00Z",
    "status": "filled"
  }
]
```

### memory/kalshi-markets-cache.json
Refreshed every cycle. Array of markets with price, volume, resolution date, 24h delta.

---

## Claude Prompt Contract

Every cron cycle, Claude receives:

```
Kalshi Autopilot cycle — {timestamp}

Markets cache: memory/kalshi-markets-cache.json
Portfolio: memory/kalshi-portfolio.json

Instructions:
1. Run risk_check.py — if not ok, report and stop
2. Read markets cache, apply TAIL and BONDING strategy logic
3. For each candidate trade, state your confidence (0-100%)
4. For trades with confidence >= 65%, run execute_trade.py
5. Run report.py with cycle summary
```

---

## Deployment Stages

### Stage 1: Demo (current)
- `KALSHI_ENV=demo`, base URL: `demo.kalshi.co`
- Paper trade for 1–2 weeks
- Validate strategy logic, state management, risk controls

### Stage 2: Live
- `KALSHI_ENV=live`, base URL: `trading-api.kalshi.com`
- Starting capital: $100–$250
- Monitor daily P&L via Telegram

---

## Cron Job

```json
{
  "id": "kalshi-autopilot",
  "name": "Kalshi Autopilot",
  "enabled": true,
  "schedule": { "kind": "cron", "expr": "*/15 * * * *" },
  "wakeMode": "now",
  "payload": { "kind": "systemEvent", "text": "..." },
  "sessionTarget": "main"
}
```
