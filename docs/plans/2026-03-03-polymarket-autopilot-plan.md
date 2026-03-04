# Polymarket Autopilot Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Deploy a 15-minute automated Polymarket trading agent on the OpenClaw VPS that executes real TAIL and BONDING trades using Python scripts orchestrated by Claude.

**Architecture:** OpenClaw cron fires a session every 15 minutes → Claude calls Python scripts (fetch, risk-check, execute, report) → Python handles all API I/O and state persistence → Claude handles all analysis and decisions.

**Tech Stack:** Python 3, py-clob-client, requests, Polymarket Gamma API, Polymarket CLOB API, Polygon wallet (ECDSA), JSON state files, Telegram notifications.

**VPS:** `ssh -p 2222 ray-openclaw@5.75.164.180`
**Container:** `docker exec -it openclaw-openclaw-gateway-1 bash`
**All script paths are inside the container at:** `/home/node/.openclaw/workspace/skills/polymarket/`

---

## Prerequisites (Manual — Do Before Task 1)

These require the user to do outside of Claude:

1. **Create a dedicated Polygon wallet** — MetaMask → "Create new account" → export private key → label it "Polymarket trading only"
2. **Fund with USDC** — Bridge or buy $20 USDC on Polygon mainnet → send to the new wallet address
3. **Create Polymarket account** — Go to polymarket.us → connect the dedicated wallet
4. **Generate API keys** — polymarket.us → top right → Developer → Generate API key → save Key, Secret, Passphrase
5. **Have Telegram bot token handy** — reuse `TELEGRAM_BOT_TOKEN` and `TELEGRAM_CHAT_ID` already in OpenClaw env

---

## Task 1: Scaffold directories and install dependency

**Files:**
- Create on VPS: `/home/node/.openclaw/workspace/skills/polymarket/` (directory)
- Create on VPS: `/home/node/.openclaw/workspace/memory/polymarket-portfolio.json`
- Create on VPS: `/home/node/.openclaw/workspace/memory/polymarket-trades.json`

**Step 1: SSH into VPS and create directory structure**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 bash -c "
mkdir -p /home/node/.openclaw/workspace/skills/polymarket
echo Created directory
ls /home/node/.openclaw/workspace/skills/
"'
```

Expected: `polymarket` appears in the listing.

**Step 2: Create initial portfolio state file**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 bash -c "
cat > /home/node/.openclaw/workspace/memory/polymarket-portfolio.json << '\''EOF'\''
{
  \"usdc_balance\": 20.00,
  \"open_positions\": [],
  \"daily_pnl\": 0.0,
  \"total_pnl\": 0.0,
  \"cycle_count\": 0,
  \"halted\": false,
  \"halt_reason\": null,
  \"last_updated\": \"2026-03-03T00:00:00Z\",
  \"daily_start_balance\": 20.00,
  \"daily_reset_date\": \"2026-03-03\"
}
EOF
cat /home/node/.openclaw/workspace/memory/polymarket-portfolio.json
"'
```

Expected: JSON printed back correctly.

**Step 3: Create empty trades history file**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 bash -c "
echo '\''[]'\'' > /home/node/.openclaw/workspace/memory/polymarket-trades.json
cat /home/node/.openclaw/workspace/memory/polymarket-trades.json
"'
```

Expected: `[]`

**Step 4: Install py-clob-client inside the container**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 bash -c "
pip3 install py-clob-client 2>&1 | tail -5
python3 -c '\''from py_clob_client.client import ClobClient; print(\"py-clob-client OK'\'')'\''
"'
```

Expected: `py-clob-client OK`

**Step 5: Commit directory scaffold locally (scripts will be added in following tasks)**

```bash
# This is VPS-only work — no local commit needed for Task 1
echo "Task 1 complete: VPS scaffold ready"
```

---

## Task 2: Write `fetch_markets.py`

**Purpose:** Query the Gamma API, filter to tradeable markets, detect TAIL and BONDING signals, output JSON to stdout.

**Files:**
- Create on VPS: `/home/node/.openclaw/workspace/skills/polymarket/fetch_markets.py`

**Step 1: Write the script**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 bash -c "
cat > /home/node/.openclaw/workspace/skills/polymarket/fetch_markets.py << '\''PYEOF'\''
#!/usr/bin/env python3
"""
Fetch active Polymarket markets and detect trading signals.

Outputs JSON list of top markets with TAIL/BONDING signal annotations.
Used as step 1 of the Polymarket Autopilot cycle.

Usage:
  python3 fetch_markets.py
  python3 fetch_markets.py --limit 20
"""
from __future__ import annotations

import argparse
import json
import sys
from datetime import datetime, timezone, timedelta

import requests

GAMMA_API = "https://gamma-api.polymarket.com"
DEFAULT_LIMIT = 50
MIN_VOLUME_24H = 1000.0    # Minimum $1k 24h volume
MAX_DAYS_TO_RESOLVE = 30   # Ignore markets resolving >30 days out
TAIL_SIGNAL_THRESHOLD = 0.90   # YES probability > 90% triggers TAIL signal
TAIL_MAX_DAYS = 7              # TAIL only valid if resolves within 7 days
BONDING_SWING_THRESHOLD = 0.10 # >10% price swing triggers BONDING signal
BONDING_MIN_DAYS = 14          # BONDING only valid if >14 days remaining


def fetch_markets(limit: int = DEFAULT_LIMIT) -> list[dict]:
    """Fetch active markets from Gamma API with basic filters."""
    url = f"{GAMMA_API}/markets"
    params = {
        "active": "true",
        "closed": "false",
        "limit": 200,  # Fetch more, filter down
        "order": "volume24hr",
        "ascending": "false",
    }
    resp = requests.get(url, params=params, timeout=15)
    resp.raise_for_status()
    markets = resp.json()
    if not isinstance(markets, list):
        markets = markets.get("markets", [])
    return markets


def parse_prices(outcome_prices_raw: str | list) -> tuple[float, float]:
    """Parse outcomePrices field → (yes_price, no_price)."""
    if isinstance(outcome_prices_raw, str):
        prices = json.loads(outcome_prices_raw)
    else:
        prices = outcome_prices_raw
    yes_price = float(prices[0]) if prices else 0.5
    no_price = float(prices[1]) if len(prices) > 1 else (1.0 - yes_price)
    return yes_price, no_price


def parse_token_ids(clob_token_ids_raw: str | list) -> tuple[str, str]:
    """Parse clobTokenIds → (yes_token_id, no_token_id)."""
    if isinstance(clob_token_ids_raw, str):
        ids = json.loads(clob_token_ids_raw)
    else:
        ids = clob_token_ids_raw or []
    yes_id = ids[0] if ids else ""
    no_id = ids[1] if len(ids) > 1 else ""
    return yes_id, no_id


def days_to_resolve(end_date_str: str) -> float | None:
    """Return days until market resolution, or None if unparseable."""
    if not end_date_str:
        return None
    try:
        end = datetime.fromisoformat(end_date_str.replace("Z", "+00:00"))
        now = datetime.now(timezone.utc)
        delta = (end - now).total_seconds() / 86400
        return max(delta, 0.0)
    except Exception:
        return None


def detect_signal(yes_price: float, no_price: float, days: float | None,
                  price_change_24h: float | None) -> str:
    """Detect TAIL, BONDING, or NONE signal."""
    if days is None:
        return "NONE"

    # TAIL: Near-certain market resolving soon — the tail is cheap
    if yes_price >= TAIL_SIGNAL_THRESHOLD and days <= TAIL_MAX_DAYS:
        return "TAIL"

    # TAIL from the NO side too
    if no_price >= TAIL_SIGNAL_THRESHOLD and days <= TAIL_MAX_DAYS:
        return "TAIL"

    # BONDING: Large recent price swing on a market with time remaining
    if (price_change_24h is not None
            and abs(price_change_24h) >= BONDING_SWING_THRESHOLD
            and days >= BONDING_MIN_DAYS):
        return "BONDING"

    return "NONE"


def filter_and_annotate(markets: list[dict], limit: int) -> list[dict]:
    """Filter markets to tradeable ones and annotate with signals."""
    now = datetime.now(timezone.utc)
    results = []

    for m in markets:
        # Skip markets without token IDs (can't trade)
        clob_ids_raw = m.get("clobTokenIds")
        if not clob_ids_raw:
            continue

        # Parse prices
        outcome_prices_raw = m.get("outcomePrices")
        if not outcome_prices_raw:
            continue
        yes_price, no_price = parse_prices(outcome_prices_raw)

        # Parse resolution date
        end_date_str = m.get("endDate") or m.get("end_date_iso")
        days = days_to_resolve(end_date_str)
        if days is None or days > MAX_DAYS_TO_RESOLVE or days <= 0:
            continue

        # Volume filter
        volume_24h = float(m.get("volume24hr") or 0)
        if volume_24h < MIN_VOLUME_24H:
            continue

        # Token IDs
        yes_token_id, no_token_id = parse_token_ids(clob_ids_raw)
        if not yes_token_id or not no_token_id:
            continue

        # Price change for BONDING detection
        price_change_24h = None
        if m.get("priceChange24h") is not None:
            price_change_24h = float(m["priceChange24h"])

        signal = detect_signal(yes_price, no_price, days, price_change_24h)

        results.append({
            "market_id": m.get("conditionId") or m.get("id"),
            "question": m.get("question", ""),
            "yes_price": round(yes_price, 4),
            "no_price": round(no_price, 4),
            "yes_token_id": yes_token_id,
            "no_token_id": no_token_id,
            "volume_24h": round(volume_24h, 2),
            "days_to_resolve": round(days, 1),
            "end_date": end_date_str,
            "price_change_24h": price_change_24h,
            "signal": signal,
        })

    # Sort: TAIL first, then BONDING, then NONE; within each group by volume
    signal_order = {"TAIL": 0, "BONDING": 1, "NONE": 2}
    results.sort(key=lambda x: (signal_order[x["signal"]], -x["volume_24h"]))

    return results[:limit]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--limit", type=int, default=DEFAULT_LIMIT)
    args = parser.parse_args()

    markets = fetch_markets(args.limit * 4)  # Over-fetch, filter down
    annotated = filter_and_annotate(markets, args.limit)

    summary = {
        "fetched_at": datetime.now(timezone.utc).isoformat(),
        "total_markets": len(annotated),
        "tail_count": sum(1 for m in annotated if m["signal"] == "TAIL"),
        "bonding_count": sum(1 for m in annotated if m["signal"] == "BONDING"),
        "markets": annotated,
    }
    print(json.dumps(summary, indent=2))


if __name__ == "__main__":
    main()
PYEOF
echo \"fetch_markets.py written\"
"'
```

**Step 2: Test it runs**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 bash -c "
python3 /home/node/.openclaw/workspace/skills/polymarket/fetch_markets.py --limit 5
"'
```

Expected: JSON with `fetched_at`, `total_markets`, `tail_count`, `bonding_count`, and `markets` array. Should have 5 markets with `signal` field.

**Step 3: Verify at least one market has a signal** (run during market hours when active)

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 bash -c "
python3 /home/node/.openclaw/workspace/skills/polymarket/fetch_markets.py | python3 -c \"
import json, sys
data = json.load(sys.stdin)
print(f\"Total: {data['\''total_markets'\'']} | TAIL: {data['\''tail_count'\'']} | BONDING: {data['\''bonding_count'\'']}\")
for m in data['\''markets'\''][:3]:
    print(f\"  [{m['\''signal'\'']:7}] {m['\''question'\''][:60]} YES={m['\''yes_price'\'']} days={m['\''days_to_resolve'\'']}\")
\"
"'
```

Expected: Summary line + 3 markets printed with signal labels.

---

## Task 3: Write `risk_check.py`

**Purpose:** Validate a Claude trade decision against hard limits. Reads portfolio state, returns ALLOW or BLOCK JSON. Claude cannot override BLOCK.

**Files:**
- Create on VPS: `/home/node/.openclaw/workspace/skills/polymarket/risk_check.py`

**Step 1: Write the script**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 bash -c "
cat > /home/node/.openclaw/workspace/skills/polymarket/risk_check.py << '\''PYEOF'\''
#!/usr/bin/env python3
"""
Validate a trade decision against hard risk limits.

Reads portfolio state from memory/polymarket-portfolio.json.
Accepts trade decision JSON as first argument or stdin.

Usage:
  echo '\''{\"action\":\"TRADE\",\"market_id\":\"0x...\",\"outcome\":\"NO\",\"quantity\":100,\"price\":0.08,\"strategy\":\"TAIL\",\"confidence\":0.72}'\'' | python3 risk_check.py
  python3 risk_check.py '\''{\"action\":\"SKIP\",\"reasoning\":\"no edge\"}'\''

Output:
  {\"result\": \"ALLOW\"}
  {\"result\": \"BLOCK\", \"reason\": \"Daily loss limit reached\"}
"""
from __future__ import annotations

import json
import sys
from datetime import datetime, timezone
from pathlib import Path

WORKSPACE = Path("/home/node/.openclaw/workspace")
PORTFOLIO_FILE = WORKSPACE / "memory" / "polymarket-portfolio.json"

# Hard limits — these cannot be overridden by Claude
MAX_POSITION_PCT = 0.05     # 5% of wallet per trade
MAX_OPEN_POSITIONS = 5      # Max concurrent open positions
DAILY_LOSS_LIMIT_PCT = 0.15 # 15% of starting daily balance
MIN_CONFIDENCE = 0.65       # Claude must report >= 65% confidence
MAX_ORDER_USDC = 10.0       # Absolute cap per order (validation phase)
MIN_EXPECTED_VALUE = 0.15   # EV = (payout * win_rate) - 1 >= 15%


def load_portfolio() -> dict:
    if not PORTFOLIO_FILE.exists():
        print(json.dumps({"result": "BLOCK", "reason": "Portfolio file not found"}))
        sys.exit(0)
    return json.loads(PORTFOLIO_FILE.read_text())


def check_daily_reset(portfolio: dict) -> dict:
    """Reset daily P&L tracker if it'\''s a new day."""
    today = datetime.now(timezone.utc).strftime("%Y-%m-%d")
    if portfolio.get("daily_reset_date") != today:
        portfolio["daily_pnl"] = 0.0
        portfolio["daily_start_balance"] = portfolio.get("usdc_balance", 0.0)
        portfolio["daily_reset_date"] = today
    return portfolio


def validate(decision: dict, portfolio: dict) -> tuple[bool, str]:
    """Return (allow, reason). allow=True means trade is permitted."""

    # SKIP decisions always pass
    if decision.get("action") == "SKIP":
        return True, "SKIP action — no trade"

    if decision.get("action") != "TRADE":
        return False, f"Unknown action: {decision.get('\''action'\'')}"

    # Check halted flag
    if portfolio.get("halted"):
        reason = portfolio.get("halt_reason", "System halted")
        return False, f"System halted: {reason}"

    # Check daily loss limit
    daily_start = portfolio.get("daily_start_balance", portfolio.get("usdc_balance", 0))
    daily_pnl = portfolio.get("daily_pnl", 0.0)
    daily_loss_pct = abs(min(daily_pnl, 0)) / max(daily_start, 1)
    if daily_loss_pct >= DAILY_LOSS_LIMIT_PCT:
        return False, f"Daily loss limit reached: {daily_loss_pct:.1%} (limit {DAILY_LOSS_LIMIT_PCT:.0%})"

    # Check open positions count
    open_count = len(portfolio.get("open_positions", []))
    if open_count >= MAX_OPEN_POSITIONS:
        return False, f"Max open positions reached: {open_count}/{MAX_OPEN_POSITIONS}"

    # Check confidence
    confidence = decision.get("confidence", 0.0)
    if confidence < MIN_CONFIDENCE:
        return False, f"Confidence too low: {confidence:.0%} (min {MIN_CONFIDENCE:.0%})"

    # Calculate trade cost
    balance = portfolio.get("usdc_balance", 0.0)
    price = float(decision.get("price", 0))
    quantity = float(decision.get("quantity", 0))
    if price <= 0 or quantity <= 0:
        return False, f"Invalid price ({price}) or quantity ({quantity})"

    trade_cost = price * quantity

    # Check absolute order cap
    if trade_cost > MAX_ORDER_USDC:
        return False, f"Order size ${trade_cost:.2f} exceeds cap ${MAX_ORDER_USDC:.2f}"

    # Check position size as % of wallet
    if balance <= 0:
        return False, f"No USDC balance: ${balance:.2f}"
    position_pct = trade_cost / balance
    if position_pct > MAX_POSITION_PCT:
        return False, f"Position size {position_pct:.1%} exceeds max {MAX_POSITION_PCT:.0%}"

    # Check sufficient balance
    if trade_cost > balance:
        return False, f"Insufficient balance: need ${trade_cost:.2f}, have ${balance:.2f}"

    # Check for duplicate position on same market
    market_id = decision.get("market_id", "")
    outcome = decision.get("outcome", "")
    for pos in portfolio.get("open_positions", []):
        if pos.get("market_id") == market_id and pos.get("outcome") == outcome:
            return False, f"Already have position on {market_id} {outcome}"

    return True, "All checks passed"


def main() -> None:
    # Read decision from arg or stdin
    if len(sys.argv) > 1:
        decision_str = sys.argv[1]
    else:
        decision_str = sys.stdin.read().strip()

    try:
        decision = json.loads(decision_str)
    except json.JSONDecodeError as e:
        print(json.dumps({"result": "BLOCK", "reason": f"Invalid JSON: {e}"}))
        sys.exit(0)

    portfolio = load_portfolio()
    portfolio = check_daily_reset(portfolio)

    allowed, reason = validate(decision, portfolio)

    if allowed:
        print(json.dumps({"result": "ALLOW", "reason": reason}))
    else:
        print(json.dumps({"result": "BLOCK", "reason": reason}))


if __name__ == "__main__":
    main()
PYEOF
echo \"risk_check.py written\"
"'
```

**Step 2: Test SKIP passes**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 bash -c "
echo '\''{\"action\":\"SKIP\",\"reasoning\":\"no edge\"}'\'' | python3 /home/node/.openclaw/workspace/skills/polymarket/risk_check.py
"'
```

Expected: `{"result": "ALLOW", "reason": "SKIP action — no trade"}`

**Step 3: Test TRADE passes with valid data**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 bash -c "
echo '\''{\"action\":\"TRADE\",\"market_id\":\"0xtest\",\"outcome\":\"NO\",\"quantity\":100,\"price\":0.05,\"strategy\":\"TAIL\",\"confidence\":0.70}'\'' | python3 /home/node/.openclaw/workspace/skills/polymarket/risk_check.py
"'
```

Expected: `{"result": "ALLOW", "reason": "All checks passed"}`

**Step 4: Test TRADE blocked by low confidence**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 bash -c "
echo '\''{\"action\":\"TRADE\",\"market_id\":\"0xtest\",\"outcome\":\"NO\",\"quantity\":100,\"price\":0.05,\"strategy\":\"TAIL\",\"confidence\":0.50}'\'' | python3 /home/node/.openclaw/workspace/skills/polymarket/risk_check.py
"'
```

Expected: `{"result": "BLOCK", "reason": "Confidence too low: 50% (min 65%)"}`

**Step 5: Test TRADE blocked by order cap**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 bash -c "
echo '\''{\"action\":\"TRADE\",\"market_id\":\"0xtest\",\"outcome\":\"YES\",\"quantity\":500,\"price\":0.50,\"strategy\":\"BONDING\",\"confidence\":0.70}'\'' | python3 /home/node/.openclaw/workspace/skills/polymarket/risk_check.py
"'
```

Expected: `{"result": "BLOCK", "reason": "Order size $250.00 exceeds cap $10.00"}`

---

## Task 4: Write `execute_trade.py`

**Purpose:** Sign and submit a real order to Polymarket CLOB using py-clob-client. Supports `--dry-run` for validation without submitting.

**Files:**
- Create on VPS: `/home/node/.openclaw/workspace/skills/polymarket/execute_trade.py`

**Step 1: Write the script**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 bash -c "
cat > /home/node/.openclaw/workspace/skills/polymarket/execute_trade.py << '\''PYEOF'\''
#!/usr/bin/env python3
"""
Execute a trade on Polymarket CLOB.

Accepts trade decision JSON (from Claude) + market details JSON (from fetch_markets).
Signs and submits order via py-clob-client.

Required env vars:
  POLYGON_PRIVATE_KEY
  POLYMARKET_API_KEY
  POLYMARKET_API_SECRET
  POLYMARKET_API_PASSPHRASE

Usage:
  python3 execute_trade.py --dry-run --decision '\''{"action":"TRADE",...}'\'' --market '\''{"market_id":"0x...","yes_token_id":"...","no_token_id":"..."}'\''
  python3 execute_trade.py --decision '\''...'\'' --market '\''...'\''
"""
from __future__ import annotations

import argparse
import json
import os
import sys
from datetime import datetime, timezone

POLYGON_CHAIN_ID = 137  # Polygon mainnet


def get_env(key: str) -> str:
    val = os.environ.get(key, "")
    if not val:
        raise EnvironmentError(f"Missing required env var: {key}")
    return val


def execute_trade_live(decision: dict, market: dict) -> dict:
    """Submit order to CLOB and return fill confirmation."""
    from py_clob_client.client import ClobClient
    from py_clob_client.clob_types import OrderArgs, OrderType

    private_key = get_env("POLYGON_PRIVATE_KEY")
    api_key = get_env("POLYMARKET_API_KEY")
    api_secret = get_env("POLYMARKET_API_SECRET")
    api_passphrase = get_env("POLYMARKET_API_PASSPHRASE")

    client = ClobClient(
        host="https://clob.polymarket.com",
        chain_id=POLYGON_CHAIN_ID,
        private_key=private_key,
        api_key=api_key,
        api_secret=api_secret,
        api_passphrase=api_passphrase,
        signature_type=1,  # POLY_PROXY
    )

    outcome = decision["outcome"].upper()
    if outcome == "YES":
        token_id = market["yes_token_id"]
    elif outcome == "NO":
        token_id = market["no_token_id"]
    else:
        raise ValueError(f"Invalid outcome: {outcome}")

    price = float(decision["price"])
    quantity = float(decision["quantity"])

    order_args = OrderArgs(
        token_id=token_id,
        price=price,
        size=quantity,
        side="BUY",
    )

    signed_order = client.create_order(order_args)
    resp = client.post_order(signed_order, OrderType.GTC)

    return {
        "status": "filled" if resp.get("success") else "failed",
        "order_id": resp.get("orderID", ""),
        "market_id": decision["market_id"],
        "outcome": outcome,
        "price": price,
        "quantity": quantity,
        "strategy": decision.get("strategy", ""),
        "executed_at": datetime.now(timezone.utc).isoformat(),
        "raw_response": resp,
    }


def execute_trade_dry(decision: dict, market: dict) -> dict:
    """Simulate trade execution without submitting."""
    outcome = decision.get("outcome", "").upper()
    price = float(decision.get("price", 0))
    quantity = float(decision.get("quantity", 0))

    return {
        "status": "dry_run",
        "order_id": "DRY_RUN_ORDER",
        "market_id": decision.get("market_id", ""),
        "outcome": outcome,
        "price": price,
        "quantity": quantity,
        "strategy": decision.get("strategy", ""),
        "executed_at": datetime.now(timezone.utc).isoformat(),
        "cost_usdc": round(price * quantity, 4),
        "raw_response": {"dry_run": True},
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true",
                        help="Simulate without submitting")
    parser.add_argument("--decision", required=True,
                        help="Trade decision JSON from Claude")
    parser.add_argument("--market", required=True,
                        help="Market details JSON from fetch_markets")
    args = parser.parse_args()

    try:
        decision = json.loads(args.decision)
        market = json.loads(args.market)
    except json.JSONDecodeError as e:
        print(json.dumps({"status": "error", "error": f"Invalid JSON: {e}"}))
        sys.exit(1)

    if decision.get("action") != "TRADE":
        print(json.dumps({"status": "skipped", "reason": "action is not TRADE"}))
        sys.exit(0)

    try:
        if args.dry_run:
            result = execute_trade_dry(decision, market)
        else:
            result = execute_trade_live(decision, market)
        print(json.dumps(result, indent=2))
    except Exception as e:
        print(json.dumps({
            "status": "error",
            "error": str(e),
            "market_id": decision.get("market_id", ""),
            "executed_at": datetime.now(timezone.utc).isoformat(),
        }))
        sys.exit(1)


if __name__ == "__main__":
    main()
PYEOF
echo \"execute_trade.py written\"
"'
```

**Step 2: Test dry-run mode**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 bash -c "
DECISION='\''{\"action\":\"TRADE\",\"market_id\":\"0xtest\",\"outcome\":\"NO\",\"quantity\":100,\"price\":0.08,\"strategy\":\"TAIL\",\"confidence\":0.72}'\''
MARKET='\''{\"market_id\":\"0xtest\",\"yes_token_id\":\"111\",\"no_token_id\":\"222\"}'\''
python3 /home/node/.openclaw/workspace/skills/polymarket/execute_trade.py --dry-run --decision \"$DECISION\" --market \"$MARKET\"
"'
```

Expected: JSON with `"status": "dry_run"`, `"order_id": "DRY_RUN_ORDER"`, `"cost_usdc": 8.0`

**Step 3: Test SKIP action exits cleanly**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 bash -c "
DECISION='\''{\"action\":\"SKIP\",\"reasoning\":\"no edge\"}'\''
MARKET='\''{\"market_id\":\"0xtest\",\"yes_token_id\":\"111\",\"no_token_id\":\"222\"}'\''
python3 /home/node/.openclaw/workspace/skills/polymarket/execute_trade.py --dry-run --decision \"$DECISION\" --market \"$MARKET\"
"'
```

Expected: `{"status": "skipped", "reason": "action is not TRADE"}`

---

## Task 5: Write `report.py`

**Purpose:** Update portfolio state after a trade, append to trade history, send Telegram notification.

**Files:**
- Create on VPS: `/home/node/.openclaw/workspace/skills/polymarket/report.py`

**Step 1: Write the script**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 bash -c "
cat > /home/node/.openclaw/workspace/skills/polymarket/report.py << '\''PYEOF'\''
#!/usr/bin/env python3
"""
Update portfolio state and send Telegram notification after a trade cycle.

Reads execution result JSON from argument or stdin.
Updates memory/polymarket-portfolio.json and memory/polymarket-trades.json.
Sends Telegram summary.

Usage:
  echo '\''{\"status\":\"dry_run\",...}'\'' | python3 report.py
  python3 report.py --result '\''{...}'\'' --reasoning \"Market at 94%...\"
  python3 report.py --skip --reasoning \"No edge this cycle\"
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

WORKSPACE = Path("/home/node/.openclaw/workspace")
PORTFOLIO_FILE = WORKSPACE / "memory" / "polymarket-portfolio.json"
TRADES_FILE = WORKSPACE / "memory" / "polymarket-trades.json"


def load_json(path: Path, default):
    if not path.exists():
        return default
    try:
        return json.loads(path.read_text())
    except Exception:
        return default


def save_json(path: Path, data) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.parent / (path.name + ".tmp")
    tmp.write_text(json.dumps(data, indent=2))
    tmp.replace(path)


def send_telegram(message: str) -> None:
    token = os.environ.get("TELEGRAM_BOT_TOKEN", "")
    chat_id = os.environ.get("TELEGRAM_CHAT_ID", "")
    if not token or not chat_id:
        print("[report] No TELEGRAM_BOT_TOKEN/CHAT_ID — skipping notification")
        return
    payload = json.dumps({
        "chat_id": chat_id,
        "text": message,
        "parse_mode": "Markdown",
    }).encode()
    url = f"https://api.telegram.org/bot{token}/sendMessage"
    try:
        req = urllib.request.Request(url, data=payload,
                                      headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=10) as r:
            r.read()
    except Exception as e:
        print(f"[report] Telegram error: {e}", file=sys.stderr)


def update_portfolio_after_trade(portfolio: dict, result: dict) -> dict:
    """Update balance and open positions after a successful trade."""
    cost = result.get("price", 0) * result.get("quantity", 0)
    portfolio["usdc_balance"] = round(portfolio.get("usdc_balance", 0) - cost, 4)
    portfolio["daily_pnl"] = round(portfolio.get("daily_pnl", 0) - cost, 4)

    position = {
        "market_id": result["market_id"],
        "outcome": result["outcome"],
        "quantity": result["quantity"],
        "entry_price": result["price"],
        "strategy": result.get("strategy", ""),
        "order_id": result.get("order_id", ""),
        "opened_at": result.get("executed_at", datetime.now(timezone.utc).isoformat()),
    }
    portfolio["open_positions"].append(position)
    portfolio["cycle_count"] = portfolio.get("cycle_count", 0) + 1
    portfolio["last_updated"] = datetime.now(timezone.utc).isoformat()
    return portfolio


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--result", default=None,
                        help="Execution result JSON from execute_trade.py")
    parser.add_argument("--reasoning", default="",
                        help="Claude'\''s reasoning text for the notification")
    parser.add_argument("--skip", action="store_true",
                        help="No trade was placed this cycle")
    args = parser.parse_args()

    portfolio = load_json(PORTFOLIO_FILE, {
        "usdc_balance": 0.0, "open_positions": [], "daily_pnl": 0.0,
        "total_pnl": 0.0, "cycle_count": 0
    })

    trades = load_json(TRADES_FILE, [])

    # Increment cycle count even for skips
    portfolio["cycle_count"] = portfolio.get("cycle_count", 0) + 1
    portfolio["last_updated"] = datetime.now(timezone.utc).isoformat()

    if args.skip or not args.result:
        save_json(PORTFOLIO_FILE, portfolio)
        msg = (
            f"*Polymarket* — Cycle #{portfolio['\''cycle_count'\'']}\n"
            f"SKIP — {args.reasoning or '\''No edge this cycle'\''}\n"
            f"Balance: ${portfolio['\''usdc_balance'\'']:.2f} | "
            f"Positions: {len(portfolio['\''open_positions'\''])} | "
            f"Daily P&L: ${portfolio['\''daily_pnl'\'']:.2f}"
        )
        print(f"[report] Cycle {portfolio['\''cycle_count'\'']} — SKIP")
        send_telegram(msg)
        return

    try:
        result = json.loads(args.result)
    except json.JSONDecodeError as e:
        print(json.dumps({"error": f"Invalid result JSON: {e}"}))
        sys.exit(1)

    status = result.get("status", "")

    if status in ("filled", "dry_run"):
        portfolio = update_portfolio_after_trade(portfolio, result)
        trades.append({**result, "reasoning": args.reasoning})
        save_json(PORTFOLIO_FILE, portfolio)
        save_json(TRADES_FILE, trades)

        cost = result.get("price", 0) * result.get("quantity", 0)
        tag = "[DRY RUN] " if status == "dry_run" else ""
        msg = (
            f"*{tag}Polymarket Trade* — Cycle #{portfolio['\''cycle_count'\'']}\n"
            f"📈 {result.get('\''strategy'\'', '\''?\'')} {result.get('\''outcome'\'', '\''?'\'')} "
            f"@ ${result.get('\''price'\'', 0):.3f}\n"
            f"Cost: ${cost:.2f} | "
            f"Market: {result.get('\''market_id'\'', '\''?'\'')[:20]}...\n"
            f"_{args.reasoning[:200]}_\n"
            f"Balance: ${portfolio['\''usdc_balance'\'']:.2f} | "
            f"Positions: {len(portfolio['\''open_positions'\''])} | "
            f"Daily P&L: ${portfolio['\''daily_pnl'\'']:.2f}"
        )
        print(f"[report] Cycle {portfolio['\''cycle_count'\'']} — TRADE {result.get('\''outcome'\'')} @ {result.get('\''price'\'')} ({status})")

    elif status == "skipped":
        save_json(PORTFOLIO_FILE, portfolio)
        msg = (
            f"*Polymarket* — Cycle #{portfolio['\''cycle_count'\'']}\n"
            f"SKIP (no action)\n"
            f"Balance: ${portfolio['\''usdc_balance'\'']:.2f}"
        )
        print(f"[report] Cycle {portfolio['\''cycle_count'\'']} — SKIPPED")

    else:
        # Error or unknown status
        save_json(PORTFOLIO_FILE, portfolio)
        msg = (
            f"*Polymarket ERROR* — Cycle #{portfolio['\''cycle_count'\'']}\n"
            f"Status: {status}\n"
            f"Error: {result.get('\''error'\'', '\''unknown'\'')[:200]}"
        )
        print(f"[report] Cycle {portfolio['\''cycle_count'\'']} — ERROR: {result.get('\''error'\'', status)}", file=sys.stderr)

    send_telegram(msg)


if __name__ == "__main__":
    main()
PYEOF
echo \"report.py written\"
"'
```

**Step 2: Test SKIP path**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 bash -c "
python3 /home/node/.openclaw/workspace/skills/polymarket/report.py --skip --reasoning \"No clear edge this cycle\"
cat /home/node/.openclaw/workspace/memory/polymarket-portfolio.json | python3 -c \"import json,sys; p=json.load(sys.stdin); print(f'Cycle {p[\\\"cycle_count\\\"]}, Balance: \${p[\\\"usdc_balance\\\"]:.2f}')\"
"'
```

Expected: `[report] Cycle 1 — SKIP` then `Cycle 1, Balance: $20.00`

**Step 3: Test TRADE path with dry-run result**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 bash -c "
RESULT='\''{\"status\":\"dry_run\",\"order_id\":\"DRY_RUN_ORDER\",\"market_id\":\"0xtest\",\"outcome\":\"NO\",\"price\":0.08,\"quantity\":100,\"strategy\":\"TAIL\",\"executed_at\":\"2026-03-03T12:00:00Z\"}'\''
python3 /home/node/.openclaw/workspace/skills/polymarket/report.py --result \"$RESULT\" --reasoning \"Market at 94% YES, tail is cheap\"
cat /home/node/.openclaw/workspace/memory/polymarket-portfolio.json | python3 -c \"import json,sys; p=json.load(sys.stdin); print(f'Cycle {p[\\\"cycle_count\\\"]}, Balance: \${p[\\\"usdc_balance\\\"]:.2f}, Positions: {len(p[\\\"open_positions\\\"])}')\"
"'
```

Expected: `[report] Cycle 2 — TRADE NO @ 0.08 (dry_run)` then `Cycle 2, Balance: $11.20, Positions: 1`

---

## Task 6: Add credentials to OpenClaw environment

**Purpose:** Make Polygon private key and Polymarket API credentials available as env vars inside the OpenClaw container.

**Files:**
- Modify on VPS: `/opt/openclaw/docker-compose.yml`

**Step 1: Backup docker-compose.yml**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
cp /opt/openclaw/docker-compose.yml /opt/openclaw/docker-compose.yml.bak-polymarket-$(date +%Y%m%d)
echo "Backup created"
'
```

**Step 2: View current environment section**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
grep -n "TELEGRAM\|OPENAI\|ANTHROPIC\|environment:" /opt/openclaw/docker-compose.yml | head -20
'
```

Note the format of existing env vars and the line numbers where they appear.

**Step 3: Add Polymarket credentials**

Edit `/opt/openclaw/docker-compose.yml` on the VPS to add the 4 new env vars in the same `environment:` section as existing vars. Replace the placeholder values with real keys before restarting:

```yaml
# Add these lines to the environment: section:
      - POLYGON_PRIVATE_KEY=YOUR_PRIVATE_KEY_HERE
      - POLYMARKET_API_KEY=YOUR_API_KEY_HERE
      - POLYMARKET_API_SECRET=YOUR_API_SECRET_HERE
      - POLYMARKET_API_PASSPHRASE=YOUR_PASSPHRASE_HERE
```

Run this command to open the file for editing on the VPS:

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 'nano /opt/openclaw/docker-compose.yml'
```

**Step 4: Restart OpenClaw to apply**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 'openclaw-restart'
```

Wait 90 seconds for startup, then verify:

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
sleep 90
docker exec openclaw-openclaw-gateway-1 bash -c "
echo POLYGON_PRIVATE_KEY is: \${POLYGON_PRIVATE_KEY:0:6}...
echo POLYMARKET_API_KEY is: \${POLYMARKET_API_KEY:0:6}...
echo API credentials loaded: \$([ -n \"\$POLYMARKET_API_KEY\" ] && echo YES || echo NO)
"'
```

Expected: Shows first 6 chars of each key (not full keys for safety) and `API credentials loaded: YES`

---

## Task 7: Register the Polymarket Autopilot cron job

**Purpose:** Add a 15-minute cron job to OpenClaw that runs the full trading cycle.

**Files:**
- Create locally: `/tmp/add_polymarket_cron.py`

**Step 1: Write the cron registration script locally**

```python
# /tmp/add_polymarket_cron.py
import json, pathlib

p = pathlib.Path("/home/node/.openclaw/cron/jobs.json")
data = json.loads(p.read_text())

existing_ids = {j["id"] for j in data["jobs"]}
print("Existing jobs:", sorted(existing_ids))

JOB_ID = "polymarket-autopilot"

if JOB_ID in existing_ids:
    print(f"Skipped (already exists): {JOB_ID}")
else:
    PROMPT = """Run the Polymarket Autopilot trading cycle.

1. Run: python3 /home/node/.openclaw/workspace/skills/polymarket/fetch_markets.py --limit 50
   Parse the JSON output. Note markets with signal=TAIL or signal=BONDING.

2. For the top 5 markets by signal priority, use Brave Search to find recent news (last 24h). Query: "<market question> news"

3. Analyze all markets + news. Select at most ONE trade opportunity using these criteria:
   - TAIL strategy: market probability >90%, resolves within 7 days, you believe the tail is mispriced
   - BONDING strategy: price swung >10% in 24h, you believe it will revert
   - Confidence must be at least 65%
   - If no strong edge exists, output SKIP

4. Output your decision as JSON. For a trade:
   {"action":"TRADE","market_id":"...","outcome":"YES or NO","quantity":<shares>,"price":<limit price>,"strategy":"TAIL or BONDING","confidence":<0-1>,"reasoning":"..."}
   Calculate quantity such that price*quantity <= $10. For example: price=0.08 → quantity=125 (cost=$10).

   For skip:
   {"action":"SKIP","reasoning":"..."}

5. Run: python3 /home/node/.openclaw/workspace/skills/polymarket/risk_check.py '<decision JSON>'
   If result is BLOCK, do NOT trade. Go to step 7.

6. If ALLOW, run: python3 /home/node/.openclaw/workspace/skills/polymarket/execute_trade.py --decision '<decision JSON>' --market '<market JSON from step 1 for this market_id>'
   (Do NOT use --dry-run for live trading. Use --dry-run for the first 5 cycles.)

7. Run: python3 /home/node/.openclaw/workspace/skills/polymarket/report.py --result '<execution result JSON or empty>' --skip (if no trade) --reasoning '<your reasoning>'

Always complete all 7 steps. Never skip reporting."""

    new_job = {
        "id": JOB_ID,
        "name": "Polymarket Autopilot",
        "enabled": True,
        "schedule": {"kind": "cron", "expr": "*/15 * * * *"},
        "state": {},
        "wakeMode": "now",
        "payload": {"kind": "systemEvent", "text": PROMPT},
        "sessionTarget": "main"
    }

    data["jobs"].append(new_job)
    p.write_text(json.dumps(data, indent=2))
    print(f"Added: {JOB_ID}")
    print(f"Total jobs: {len(data['jobs'])}")
```

**Step 2: Copy and run the script on the VPS container**

```bash
scp -P 2222 /tmp/add_polymarket_cron.py ray-openclaw@5.75.164.180:/tmp/
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker cp /tmp/add_polymarket_cron.py openclaw-openclaw-gateway-1:/tmp/
docker exec openclaw-openclaw-gateway-1 python3 /tmp/add_polymarket_cron.py
'
```

Expected: `Added: polymarket-autopilot` and `Total jobs: N`

**Step 3: Verify job was registered**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 python3 -c "
import json
data = json.loads(open('\''/home/node/.openclaw/cron/jobs.json'\'').read())
jobs = {j['\''id'\'']: j['\''schedule'\''] for j in data['\''jobs'\'']}
print(jobs.get('\''polymarket-autopilot'\'', '\''NOT FOUND'\''))
"'
```

Expected: `{'kind': 'cron', 'expr': '*/15 * * * *'}`

---

## Task 8: End-to-end dry run validation

**Purpose:** Validate the full pipeline manually before the cron goes live. Run all 4 scripts in sequence with a real market from the API.

**Step 1: Reset portfolio to clean state**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 bash -c "
cat > /home/node/.openclaw/workspace/memory/polymarket-portfolio.json << '\''EOF'\''
{
  \"usdc_balance\": 20.00,
  \"open_positions\": [],
  \"daily_pnl\": 0.0,
  \"total_pnl\": 0.0,
  \"cycle_count\": 0,
  \"halted\": false,
  \"halt_reason\": null,
  \"last_updated\": \"2026-03-03T00:00:00Z\",
  \"daily_start_balance\": 20.00,
  \"daily_reset_date\": \"2026-03-03\"
}
EOF
echo '\''Portfolio reset'\''
"'
```

**Step 2: Fetch real markets and grab the top TAIL market**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 bash -c "
python3 /home/node/.openclaw/workspace/skills/polymarket/fetch_markets.py --limit 10 > /tmp/markets.json
echo \"Markets fetched. Top signals:\"
python3 -c \"
import json
data = json.load(open('/tmp/markets.json'))
for m in data['markets'][:5]:
    print(f\"  [{m['signal']:7}] {m['question'][:60]} YES={m['yes_price']} days={m['days_to_resolve']}\")
\"
"'
```

**Step 3: Simulate a TAIL trade on the first TAIL market**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 bash -c "
# Get first TAIL market
MARKET=\$(python3 -c \"
import json
data = json.load(open('/tmp/markets.json'))
tail = next((m for m in data['markets'] if m['signal'] == 'TAIL'), None)
if tail:
    print(json.dumps(tail))
else:
    print('No TAIL signal found')
\")
echo \"Market: \$MARKET\" | cut -c1-100

# Build a simulated TAIL decision (NO side since YES is the near-certain outcome)
DECISION=\$(python3 -c \"
import json
data = json.load(open('/tmp/markets.json'))
tail = next((m for m in data['markets'] if m['signal'] == 'TAIL'), None)
if not tail:
    print(json.dumps({'action': 'SKIP', 'reasoning': 'No TAIL market available'}))
else:
    # Buy NO if YES > 90% (the tail)
    outcome = 'NO' if tail['yes_price'] >= 0.90 else 'YES'
    price = tail['no_price'] if outcome == 'NO' else tail['yes_price']
    price = round(max(price, 0.02), 4)  # At least 2 cents
    quantity = int(10.0 / price)  # Max $10 order
    print(json.dumps({
        'action': 'TRADE',
        'market_id': tail['market_id'],
        'outcome': outcome,
        'quantity': quantity,
        'price': price,
        'strategy': 'TAIL',
        'confidence': 0.68,
        'reasoning': 'Dry run validation — testing pipeline'
    }))
\")
echo \"Decision: \$DECISION\"

# Risk check
RISK=\$(echo \"\$DECISION\" | python3 /home/node/.openclaw/workspace/skills/polymarket/risk_check.py)
echo \"Risk check: \$RISK\"

# Execute dry run
if [ \"\$(echo \$RISK | python3 -c 'import json,sys; print(json.load(sys.stdin)[\"result\"])')\"\  = \"ALLOW\" ]; then
  EXEC=\$(python3 /home/node/.openclaw/workspace/skills/polymarket/execute_trade.py --dry-run --decision \"\$DECISION\" --market \"\$MARKET\")
  echo \"Execute: \$EXEC\"
  # Report
  python3 /home/node/.openclaw/workspace/skills/polymarket/report.py --result \"\$EXEC\" --reasoning \"Dry run validation\"
else
  python3 /home/node/.openclaw/workspace/skills/polymarket/report.py --skip --reasoning \"Risk check blocked: \$RISK\"
fi
"'
```

Expected: All 4 scripts run in sequence. No errors. Final output shows `Cycle 1 — TRADE NO @ ... (dry_run)` or `SKIP` depending on available markets.

**Step 4: Verify portfolio updated**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 bash -c "
cat /home/node/.openclaw/workspace/memory/polymarket-portfolio.json
"'
```

Expected: `cycle_count: 1`, and either a position in `open_positions` or still empty (if SKIP), and balance updated if trade occurred.

**Step 5: Enable live trading mode**

Once dry run validates successfully (at least 3 cycles with no errors), update the cron job prompt to remove `--dry-run`:

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 python3 -c "
import json, pathlib
p = pathlib.Path('\'''/home/node/.openclaw/cron/jobs.json'\'')
data = json.loads(p.read_text())
job = next(j for j in data['\''jobs'\''] if j['\''id'\''] == '\''polymarket-autopilot'\'')
# Change \"(Do NOT use --dry-run for live trading. Use --dry-run for the first 5 cycles.)\"
# to remove the dry-run instruction — edit the prompt text accordingly
print(\"Current prompt lines:\", len(job['\''payload'\'']['\''text'\''].splitlines()))
"'
```

Then manually edit the cron job payload to remove the `--dry-run` instruction (using the same pattern as add_polymarket_cron.py but updating the existing job).

---

## Post-Deployment Monitoring

**Check portfolio status anytime:**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 python3 -c "
import json
p = json.load(open(\"'\''/home/node/.openclaw/workspace/memory/polymarket-portfolio.json'\''\"))
print(f\"Balance: \${p['\''usdc_balance'\'']:.2f} | Cycles: {p['\''cycle_count'\'']}\")
print(f\"Daily P&L: \${p['\''daily_pnl'\'']:.2f} | Total P&L: \${p['\''total_pnl'\'']:.2f}\")
print(f\"Open positions: {len(p['\''open_positions'\''])}\")
for pos in p['\''open_positions'\'']:
    print(f\"  {pos['\''strategy'\'']:7} {pos['\''outcome'\'']:3} @ {pos['\''entry_price'\'']:.3f} x {pos['\''quantity'\'']}\")
"'
```

**Check trade history:**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 python3 -c "
import json
trades = json.load(open(\"'\''/home/node/.openclaw/workspace/memory/polymarket-trades.json'\''\"))
print(f\"Total trades: {len(trades)}\")
for t in trades[-5:]:
    print(f\"  {t.get('\''executed_at'\'','\''?'\'')[:10]} {t.get('\''strategy'\'','\''?'\''):7} {t.get('\''outcome'\'','\''?'\''):3} @ {t.get('\''price'\'',0):.3f} - {t.get('\''status'\'','\''?'\'')}\")"'
```

**Emergency halt:**

```bash
ssh -p 2222 ray-openclaw@5.75.164.180 '
docker exec openclaw-openclaw-gateway-1 python3 -c "
import json
p = json.load(open(\"'\''/home/node/.openclaw/workspace/memory/polymarket-portfolio.json'\''\"))
p['\''halted'\''] = True
p['\''halt_reason'\''] = '\''Manual halt'\''
open(\"'\''/home/node/.openclaw/workspace/memory/polymarket-portfolio.json'\''\", '\''w'\'').write(json.dumps(p, indent=2))
print('\''System halted'\'')
"'
```
