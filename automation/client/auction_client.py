import asyncio
import json
import sys
import time
from dataclasses import dataclass
from decimal import Decimal
from typing import Any, Dict, Optional

import websockets

BASE_URL = "http://localhost:8080"
ITEM_ID = ""  # required (UUID string)

USER_ID = ""
MAX_BID = Decimal("0")


# =========================


@dataclass
class AuctionState:
    status: Optional[str] = None
    current_price: Optional[Decimal] = None
    min_increment: Optional[Decimal] = None
    current_winner_user_id: Optional[str] = None


def http_to_ws(base_url: str) -> str:
    u = base_url.rstrip("/")
    if u.startswith("https://"):
        return "wss://" + u[len("https://"):]
    if u.startswith("http://"):
        return "ws://" + u[len("http://"):]
    return u  # allow ws:// passed directly


def parse_decimal(value: Any) -> Optional[Decimal]:
    if value is None:
        return None
    return Decimal(str(value))


def update_state_from_event(state: AuctionState, event: Dict[str, Any]) -> None:
    auction = event.get("auction") or {}
    state.status = auction.get("status") or state.status
    state.current_price = parse_decimal(auction.get("currentPrice")) or state.current_price
    state.min_increment = parse_decimal(auction.get("minIncrement")) or state.min_increment
    state.current_winner_user_id = auction.get("currentWinnerUserId") or state.current_winner_user_id


def is_auto_bid_enabled() -> bool:
    return bool(USER_ID) and MAX_BID is not None and MAX_BID > 0


async def receiver_loop(ws, state: AuctionState):
    last_bid_attempt_at = 0.0
    auto_bid = is_auto_bid_enabled()

    if auto_bid:
        print(f"[auto-bid] enabled user={USER_ID} max={MAX_BID}")
    else:
        print("[auto-bid] disabled (set USER_ID and MAX_BID > 0 to enable)")

    async for msg in ws:
        try:
            payload = json.loads(msg)
        except Exception:
            print(f"[ws] non-json: {msg}")
            continue

        if payload.get("type") == "ERROR":
            print(f"[error] {payload.get('message')}")
            continue

        update_state_from_event(state, payload)

        ts = payload.get("timestamp")
        et = payload.get("type")
        print(
            f"[{ts}] {et} status={state.status} price={state.current_price} winner={state.current_winner_user_id}"
        )

        if not auto_bid:
            continue

        if state.status != "OPEN":
            continue
        if state.current_price is None or state.min_increment is None:
            continue
        if state.current_winner_user_id == USER_ID:
            continue

        next_bid = state.current_price + state.min_increment
        if next_bid > MAX_BID:
            print(f"[auto-bid] next={next_bid} exceeds max={MAX_BID}; stopping auto-bid")
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
                    "itemId": ITEM_ID,
                    "bidderUserId": USER_ID,
                    "amount": float(next_bid),
                }
            )
        )
        print(f"[auto-bid] placed {next_bid}")


async def input_loop(ws):
    """
    Manual interaction:
    - Type a number to place a bid with that amount
    - Type 'q' to quit
    """
    if not USER_ID:
        print("[manual] USER_ID is empty, manual bidding is disabled.")
        return

    print("[manual] type a number to bid, or 'q' to quit.")
    while True:
        line = await asyncio.to_thread(sys.stdin.readline)
        if not line:
            return
        line = line.strip()
        if line.lower() in ("q", "quit", "exit"):
            return
        try:
            amount = Decimal(line)
        except Exception:
            print("[manual] invalid number")
            continue

        await ws.send(
            json.dumps(
                {
                    "type": "PLACE_BID",
                    "itemId": ITEM_ID,
                    "bidderUserId": USER_ID,
                    "amount": float(amount),
                }
            )
        )
        print(f"[manual] sent bid={amount}")


async def main():
    if not ITEM_ID:
        print("Set ITEM_ID at the top of this script first.")
        raise SystemExit(2)

    ws_url = http_to_ws(BASE_URL) + "/ws"
    print(f"Connecting to {ws_url}")

    async with websockets.connect(ws_url, ping_interval=20, ping_timeout=20) as ws:
        await ws.send(json.dumps({"type": "SUBSCRIBE", "itemId": ITEM_ID}))

        state = AuctionState()
        await asyncio.gather(
            receiver_loop(ws, state),
            input_loop(ws),
        )


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("bye")
