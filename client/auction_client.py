import argparse
import asyncio
import json
import sys
import time
from dataclasses import dataclass
from decimal import Decimal
from typing import Any, Dict, Optional

import websockets


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


def http_to_ws(base_url: str) -> str:
    u = base_url.rstrip("/")
    if u.startswith("https://"):
        return "wss://" + u[len("https://") :]
    if u.startswith("http://"):
        return "ws://" + u[len("http://") :]
    # Allow passing ws:// directly
    return u


def update_state_from_event(state: AuctionState, event: Dict[str, Any]) -> None:
    auction = event.get("auction") or {}
    state.status = auction.get("status") or state.status
    state.current_price = parse_decimal(auction.get("currentPrice")) or state.current_price
    state.min_increment = parse_decimal(auction.get("minIncrement")) or state.min_increment
    state.current_winner_user_id = auction.get("currentWinnerUserId") or state.current_winner_user_id


async def run(argv) -> int:
    parser = argparse.ArgumentParser(description="Real-time auction CLI (WebSocket + auto-bid)")
    parser.add_argument("--base-url", default="http://localhost:8080", help="API base URL (http/https)")
    parser.add_argument("--item-id", required=True, help="Item UUID")
    parser.add_argument("--user-id", help="Your user UUID (required for auto-bid)")
    parser.add_argument("--max-bid", type=Decimal, help="Max bid limit (enables auto-bid)")
    ns = parser.parse_args(argv)

    base_url = ns.base_url.rstrip("/")
    item_id = ns.item_id
    user_id = ns.user_id
    max_bid = ns.max_bid

    auto_bid = user_id is not None and max_bid is not None
    if (user_id is None) ^ (max_bid is None):
        print("If you set --max-bid you must also set --user-id (and vice-versa).", file=sys.stderr)
        return 2

    state = AuctionState()

    ws_url = http_to_ws(base_url) + "/ws"
    print(f"Connecting to {ws_url}")
    if auto_bid:
        print(f"Auto-bid enabled: user={user_id} max={max_bid}")

    last_bid_attempt_at = 0.0

    while True:
        try:
            async with websockets.connect(ws_url, ping_interval=20, ping_timeout=20) as ws:
                await ws.send(json.dumps({"type": "SUBSCRIBE", "itemId": item_id}))

                async for msg in ws:
                    try:
                        event = json.loads(msg)
                    except Exception:
                        print(f"[ws] (non-json) {msg}")
                        continue

                    if event.get("type") == "ERROR":
                        print(f"[error] {event.get('message')}")
                        continue

                    update_state_from_event(state, event)
                    print(
                        f"[{event.get('timestamp')}] {event.get('type')} "
                        f"status={state.status} price={state.current_price} winner={state.current_winner_user_id}"
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

                    now = time.time()
                    if now - last_bid_attempt_at < 0.25:
                        continue
                    last_bid_attempt_at = now

                    await ws.send(
                        json.dumps(
                            {
                                "type": "PLACE_BID",
                                "itemId": item_id,
                                "bidderUserId": user_id,
                                "amount": float(next_bid),
                            }
                        )
                    )
                    print(f"[auto-bid] placed {next_bid}")
        except KeyboardInterrupt:
            print("bye")
            return 0
        except Exception as e:
            print(f"[warn] WebSocket disconnected: {e} (reconnecting in 1s)", file=sys.stderr)
            time.sleep(1)


if __name__ == "__main__":
    raise SystemExit(asyncio.run(run(sys.argv[1:])))

