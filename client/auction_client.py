import argparse
import json
import sys
import time
from dataclasses import dataclass
from decimal import Decimal
from typing import Any, Dict, Optional, Tuple

import requests


@dataclass
class AuctionState:
    status: Optional[str] = None
    current_price: Optional[Decimal] = None
    min_increment: Optional[Decimal] = None
    current_winner_user_id: Optional[str] = None


def parse_decimal(value: Any) -> Optional[Decimal]:
    if value is None:
        return None
    # Server returns JSON number; keep decimal semantics.
    return Decimal(str(value))


def fetch_snapshot(base_url: str, item_id: str) -> Dict[str, Any]:
    r = requests.get(f"{base_url}/api/items/{item_id}", timeout=10)
    r.raise_for_status()
    return r.json()


def place_bid(base_url: str, item_id: str, user_id: str, amount: Decimal) -> Tuple[bool, str]:
    payload = {"bidderUserId": user_id, "amount": float(amount)}
    r = requests.post(f"{base_url}/api/items/{item_id}/bids", json=payload, timeout=10)
    if r.status_code >= 200 and r.status_code < 300:
        return True, "ok"
    try:
        body = r.json()
        msg = body.get("message") or str(body)
    except Exception:
        msg = r.text
    return False, f"{r.status_code}: {msg}"


def sse_events(url: str):
    """
    Minimal SSE parser: yields (event_name, data_json_str).
    """
    with requests.get(url, stream=True, timeout=60) as r:
        r.raise_for_status()
        event_name = None
        data_lines = []
        for raw_line in r.iter_lines(decode_unicode=True):
            if raw_line is None:
                continue
            line = raw_line.strip("\r")
            if line == "":
                if data_lines:
                    yield event_name, "\n".join(data_lines)
                event_name = None
                data_lines = []
                continue
            if line.startswith(":"):
                continue
            if line.startswith("event:"):
                event_name = line[len("event:") :].strip()
                continue
            if line.startswith("data:"):
                data_lines.append(line[len("data:") :].lstrip())
                continue


def update_state_from_event(state: AuctionState, event: Dict[str, Any]) -> None:
    auction = event.get("auction") or {}
    state.status = auction.get("status") or state.status
    state.current_price = parse_decimal(auction.get("currentPrice")) or state.current_price
    state.min_increment = parse_decimal(auction.get("minIncrement")) or state.min_increment
    state.current_winner_user_id = auction.get("currentWinnerUserId") or state.current_winner_user_id


def main() -> int:
    parser = argparse.ArgumentParser(description="Real-time auction CLI (SSE + auto-bid)")
    parser.add_argument("--base-url", default="http://localhost:8080", help="API base URL")
    parser.add_argument("--item-id", required=True, help="Item UUID")
    parser.add_argument("--user-id", help="Your user UUID (required for auto-bid)")
    parser.add_argument("--max-bid", type=Decimal, help="Max bid limit (enables auto-bid)")
    args = parser.parse_args()

    base_url = args.base_url.rstrip("/")
    item_id = args.item_id
    user_id = args.user_id
    max_bid = args.max_bid

    auto_bid = user_id is not None and max_bid is not None
    if (user_id is None) ^ (max_bid is None):
        print("If you set --max-bid you must also set --user-id (and vice-versa).", file=sys.stderr)
        return 2

    state = AuctionState()

    # Best-effort snapshot (works even without SSE).
    try:
        snap = fetch_snapshot(base_url, item_id)
        if snap.get("auction"):
            update_state_from_event(state, {"auction": snap["auction"]})
    except Exception as e:
        print(f"[warn] snapshot fetch failed: {e}", file=sys.stderr)

    events_url = f"{base_url}/api/items/{item_id}/events"
    print(f"Connecting to {events_url}")
    if auto_bid:
        print(f"Auto-bid enabled: user={user_id} max={max_bid}")

    last_bid_attempt_at = 0.0

    while True:
        try:
            for event_name, data_str in sse_events(events_url):
                try:
                    event = json.loads(data_str)
                except Exception:
                    print(f"[event {event_name}] (non-json) {data_str}")
                    continue

                update_state_from_event(state, event)

                print(
                    f"[{event.get('timestamp')}] {event.get('type')} "
                    f"status={state.status} price={state.current_price} "
                    f"winner={state.current_winner_user_id}"
                )

                if not auto_bid:
                    continue

                if state.status != "OPEN":
                    continue
                if state.current_price is None or state.min_increment is None:
                    continue
                if state.current_winner_user_id == user_id:
                    continue

                next_bid = state.current_price + state.min_increment
                if next_bid > max_bid:
                    print(f"[auto-bid] next={next_bid} exceeds max={max_bid}, stopping auto-bid")
                    auto_bid = False
                    continue

                # Throttle: avoid hammering if multiple events arrive quickly.
                now = time.time()
                if now - last_bid_attempt_at < 0.25:
                    continue
                last_bid_attempt_at = now

                ok, msg = place_bid(base_url, item_id, user_id, next_bid)
                if ok:
                    print(f"[auto-bid] placed {next_bid}")
                else:
                    print(f"[auto-bid] rejected ({msg})")

        except KeyboardInterrupt:
            print("bye")
            return 0
        except Exception as e:
            print(f"[warn] SSE disconnected: {e} (reconnecting in 1s)", file=sys.stderr)
            time.sleep(1)


if __name__ == "__main__":
    raise SystemExit(main())

