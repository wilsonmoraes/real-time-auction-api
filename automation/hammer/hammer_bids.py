import concurrent.futures
import json
import random
import time
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from decimal import Decimal
from typing import Dict, Tuple

import requests

# =========================
# Demo configuration (edit)
# =========================
BASE_URL = "http://localhost:8080"

DISPLAY_NAME = "hammer-user"
ITEM_NAME = "Hammered Item"
ITEM_DESCRIPTION = "Created by automation/hammer/hammer_bids.py"

START_DELAY_SECONDS = 5
END_AFTER_SECONDS = 60
STARTING_PRICE = Decimal("10.00")
MIN_INCREMENT = Decimal("1.00")

CONCURRENCY = 25
TOTAL_REQUESTS = 80

# Mix of bids:
# - some are too low on purpose (to trigger auction.bids.rejected{reason="bid_too_low"})
# - some are valid and increasing (to drive accepted bids)
TOO_LOW_RATIO = 0.50
VALID_BID_MIN = Decimal("11.00")
VALID_BID_MAX = Decimal("40.00")

REQUEST_TIMEOUT_SECONDS = 10


# =========================


@dataclass
class Created:
    user_id: str
    item_id: str


def iso_utc(dt: datetime) -> str:
    return dt.astimezone(timezone.utc).isoformat().replace("+00:00", "Z")


def post_json(url: str, payload: dict) -> Tuple[int, dict]:
    r = requests.post(url, json=payload, timeout=REQUEST_TIMEOUT_SECONDS)
    try:
        body = r.json()
    except Exception:
        body = {"raw": r.text}
    return r.status_code, body


def get_json(url: str) -> Tuple[int, dict]:
    r = requests.get(url, timeout=REQUEST_TIMEOUT_SECONDS)
    try:
        body = r.json()
    except Exception:
        body = {"raw": r.text}
    return r.status_code, body


def create_user() -> str:
    status, body = post_json(f"{BASE_URL}/api/users", {"displayName": DISPLAY_NAME})
    if status != 201:
        raise RuntimeError(f"Create user failed: {status} {body}")
    return body["id"]


def create_item() -> str:
    status, body = post_json(
        f"{BASE_URL}/api/items",
        {"name": ITEM_NAME, "description": ITEM_DESCRIPTION},
    )
    if status != 201:
        raise RuntimeError(f"Create item failed: {status} {body}")
    return body["id"]


def schedule_auction(item_id: str) -> None:
    now = datetime.now(timezone.utc)
    start = now + timedelta(seconds=START_DELAY_SECONDS)
    end = now + timedelta(seconds=END_AFTER_SECONDS)

    payload = {
        "startTime": iso_utc(start),
        "endTime": iso_utc(end),
        "startingPrice": float(STARTING_PRICE),
        "minIncrement": float(MIN_INCREMENT),
    }
    status, body = post_json(f"{BASE_URL}/api/items/{item_id}/auction", payload)
    if status != 201:
        raise RuntimeError(f"Schedule auction failed: {status} {body}")

    print(f"[setup] scheduled auction item={item_id}")
    print(f"[setup] startTime={payload['startTime']} endTime={payload['endTime']}")


def wait_until_open(item_id: str) -> dict:
    print("[wait] waiting for auction to become OPEN ...")
    deadline = time.time() + 90
    while time.time() < deadline:
        status, body = get_json(f"{BASE_URL}/api/items/{item_id}")
        if status != 200:
            print(f"[wait] GET item failed: {status} {body}")
            time.sleep(0.5)
            continue

        auction = body.get("auction")
        if not auction:
            print("[wait] auction not found yet")
            time.sleep(0.5)
            continue

        auction_status = auction.get("status")
        current = auction.get("currentPrice")
        min_inc = auction.get("minIncrement")
        print(f"[wait] status={auction_status} current={current} minInc={min_inc}")

        if auction_status == "OPEN":
            return body
        time.sleep(0.5)

    raise RuntimeError("Auction did not become OPEN in time")


def categorize_rejection(body: dict) -> str:
    msg = (body.get("message") or "").lower()
    if "too low" in msg:
        return "bid_too_low"
    if "not open" in msg:
        return "auction_not_open"
    if "unknown user" in msg:
        return "unknown_user"
    if "amount" in msg:
        return "invalid_amount"
    return "other"


def place_bid(item_id: str, user_id: str, amount: Decimal) -> Tuple[bool, str, float]:
    t0 = time.perf_counter()
    status, body = post_json(
        f"{BASE_URL}/api/items/{item_id}/bids",
        {"bidderUserId": user_id, "amount": float(amount)},
    )
    dt_ms = (time.perf_counter() - t0) * 1000.0

    if status == 201:
        return True, "accepted", dt_ms
    if status == 400:
        return False, categorize_rejection(body), dt_ms
    return False, f"http_{status}", dt_ms


def generate_bid_amount() -> Decimal:
    if random.random() < TOO_LOW_RATIO:
        # intentionally too low
        return Decimal(str(random.choice([0.5, 1.0, 2.0, 5.0])))
    # valid-ish range
    raw = random.uniform(float(VALID_BID_MIN), float(VALID_BID_MAX))
    # keep 2 decimals
    return Decimal(str(raw)).quantize(Decimal("0.01"))


def main():
    random.seed(42)

    print("=== Auction Concurrency Hammer ===")
    print(f"[config] baseUrl={BASE_URL}")
    print(f"[config] concurrency={CONCURRENCY} totalRequests={TOTAL_REQUESTS}")

    user_id = create_user()
    item_id = create_item()
    print(f"[setup] userId={user_id}")
    print(f"[setup] itemId={item_id}")

    schedule_auction(item_id)
    wait_until_open(item_id)

    print("[hammer] firing concurrent bids ...")
    accepted = 0
    rejected: Dict[str, int] = {}
    latencies_ok = []
    latencies_fail = []

    def task(_i: int):
        amount = generate_bid_amount()
        ok, reason, ms = place_bid(item_id, user_id, amount)
        return _i, amount, ok, reason, ms

    with concurrent.futures.ThreadPoolExecutor(max_workers=CONCURRENCY) as ex:
        futures = [ex.submit(task, i) for i in range(TOTAL_REQUESTS)]
        for fut in concurrent.futures.as_completed(futures):
            i, amount, ok, reason, ms = fut.result()
            if ok:
                accepted += 1
                latencies_ok.append(ms)
                print(f"[OK #{i:03d}] bid={amount} latency={ms:.1f}ms")
            else:
                rejected[reason] = rejected.get(reason, 0) + 1
                latencies_fail.append(ms)
                print(f"[REJECT #{i:03d}] bid={amount} reason={reason} latency={ms:.1f}ms")

    status, body = get_json(f"{BASE_URL}/api/items/{item_id}")
    auction = (body.get("auction") or {}) if status == 200 else {}

    def pct(values):
        return f"{(sum(values) / len(values)):.1f}ms avg" if values else "n/a"

    print("\n=== Summary ===")
    print(f"itemId={item_id}")
    print(f"userId={user_id}")
    print(f"accepted={accepted}")
    print(f"rejected={json.dumps(rejected, indent=2)}")
    print(f"latency_ok={pct(latencies_ok)} latency_reject={pct(latencies_fail)}")
    print(
        "finalAuction="
        + json.dumps(
            {
                "status": auction.get("status"),
                "currentPrice": auction.get("currentPrice"),
                "minIncrement": auction.get("minIncrement"),
                "currentWinnerUserId": auction.get("currentWinnerUserId"),
                "closedAt": auction.get("closedAt"),
            },
            indent=2,
        )
    )
    print("\nTip: open Spring Boot Admin -> Metrics and check auction.bids.* after running this script.")


if __name__ == "__main__":
    main()
