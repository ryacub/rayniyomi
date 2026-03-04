# Polymarket Autopilot Design

## Overview

An automated real-money trading system for Polymarket that runs inside OpenClaw on the VPS. Claude acts as the brain (market analysis, trading decisions), Python scripts handle the I/O (API calls, order execution, state persistence). Runs on a 15-minute cron cycle.

---

## Goals

- Execute real trades on Polymarket autonomously every 15 minutes
- Focus on TAIL and BONDING strategies — avoid SPREAD (too fast for LLMs)
- Start with minimum capital ($10–20 USDC) to validate execution pipeline, then scale
- Hard risk controls enforced in Python, not delegated to Claude
- Minimal human intervention required once live

---

## Architecture

### Hybrid: OpenClaw Orchestrates, Python Executes

```
OpenClaw Cron (every 15 min)
    │
    ▼
STEP 1: fetch_markets.py
    └── Calls Gamma API → returns top 50 active markets
    │
    ▼
STEP 2: Brave Search (OpenClaw tool)
    └── News headlines for most interesting markets
    │
    ▼
STEP 3: Claude Analysis (in-session)
    └── Reads markets + news
    └── Selects TAIL/BONDING opportunities
    └── Outputs JSON trade decision or SKIP
    │
    ▼
STEP 4: risk_check.py
    └── Validates decision against hard limits
    └── Returns ALLOW or BLOCK with reason
    │
    ▼
STEP 5: execute_trade.py (if ALLOW)
    └── Signs and submits order via py-clob-client
    └── Returns fill confirmation or error
    │
    ▼
STEP 6: report.py
    └── Updates portfolio state JSON
    └── Logs trade to trades history
    └── Sends Telegram notification
```

### VPS File Layout

```
/home/node/.openclaw/workspace/
├── skills/
│   └── polymarket/
│       ├── fetch_markets.py      # Gamma API → filtered market list
│       ├── risk_check.py         # Hard limit validation
│       ├── execute_trade.py      # CLOB order submission
│       └── report.py             # State update + notifications
└── memory/
    ├── polymarket-portfolio.json # Live positions, balance, P&L
    └── polymarket-trades.json    # Trade history (all fills)
```

---

## Trading Strategies

### TAIL
- **Signal:** Market probability > 90%, resolves within 7 days
- **Trade:** Buy the underpriced side (e.g. NO at $0.05 if YES = 95%)
- **Thesis:** Markets over-price near-certainties; the tail is statistically cheap
- **Risk:** Low win rate (~15–20%), but 5–20x payout when right
- **Position size:** Small (2–3% of wallet)

### BONDING
- **Signal:** Price moved > 10% in last 24 hours on a market with >14 days remaining
- **Trade:** Bet on mean reversion (fade the overreaction)
- **Thesis:** Liquidity-driven swings on prediction markets often snap back
- **Risk:** Domain knowledge required; 40–60% win rate with good filter
- **Position size:** Moderate (3–5% of wallet)

### SPREAD
- **Status:** NOT PURSUED
- **Reason:** Average window is 2.7s; 73% captured by <100ms bots. LLM cycle can't compete.

---

## Risk Controls (Hard Limits in Python)

All enforced in `risk_check.py` — Claude cannot override these:

| Limit | Value | Rule |
|-------|-------|------|
| Max position size | 5% of wallet | Per trade |
| Max open positions | 5 concurrent | Total |
| Daily loss limit | 15% of starting balance | Halt for the day |
| Min confidence threshold | 65% | Claude must express in JSON |
| Min expected value | 15% | EV = (payout × win_rate) - 1 |
| Max order size | $10 USDC | Absolute cap while validating |

---

## State: `polymarket-portfolio.json`

```json
{
  "usdc_balance": 47.30,
  "open_positions": [
    {
      "market_id": "0xabc123...",
      "question": "Will GPT-5 release before April 2026?",
      "outcome": "NO",
      "quantity": 50,
      "entry_price": 0.08,
      "strategy": "TAIL",
      "opened_at": "2026-03-03T14:00:00Z"
    }
  ],
  "daily_pnl": -1.20,
  "total_pnl": 3.40,
  "cycle_count": 12,
  "halted": false,
  "halt_reason": null
}
```

---

## Claude Prompt Contract

Each cycle, Claude receives:
1. Top 50 markets (filtered for active, >$1k volume, resolves <30 days)
2. News headlines for top 10 by interest
3. Current portfolio state (positions, balance, daily P&L)

Claude must respond with JSON:

```json
{
  "action": "TRADE",
  "market_id": "0xabc...",
  "outcome": "NO",
  "quantity": 100,
  "strategy": "TAIL",
  "confidence": 0.72,
  "reasoning": "Market at 94% YES but binary resolution tomorrow — tail is mispriced"
}
```

Or:
```json
{
  "action": "SKIP",
  "reasoning": "No clear edge this cycle"
}
```

---

## Credentials Required

These must be added to OpenClaw secrets before going live:

| Variable | Source |
|----------|--------|
| `POLYGON_PRIVATE_KEY` | Polygon wallet (MetaMask or new wallet) |
| `POLYMARKET_API_KEY` | polymarket.us → Developer tab |
| `POLYMARKET_API_SECRET` | Same |
| `POLYMARKET_API_PASSPHRASE` | Same |

**Important:** Use a dedicated wallet — never the primary. Start with $20 USDC.

---

## Cron Schedule

```json
{
  "id": "polymarket-autopilot",
  "schedule": { "kind": "cron", "expr": "*/15 * * * *" },
  "wakeMode": "now",
  "sessionTarget": "main"
}
```

---

## Deployment Stages

1. **Credential setup** — Polygon wallet + Polymarket API keys + USDC funding
2. **Script validation** — Test each Python script independently (dry run mode)
3. **Cron integration** — Add to OpenClaw, monitor first 10 cycles manually
4. **Live with $20** — Execute real trades, watch for errors and slippage
5. **Scale** — Add capital after 50+ cycles with positive or near-breakeven P&L

---

## Known Risks

| Risk | Mitigation |
|------|-----------|
| Wallet compromise | Dedicated wallet, $20 start, never primary keys |
| Claude makes bad trades | Hard Python risk controls, daily loss halt |
| API auth failure | Scripts return errors → Claude logs and skips |
| Slippage on fills | Limit orders only, small position sizes |
| Market resolves wrong | Accepted — prediction markets have resolution risk |
