## Auction CLI (Python)

Simple command-line client that:
- Subscribes to real-time auction events via **SSE**
- Prints auction state updates
- Optionally auto-bids up to a max limit (always bidding the minimum needed to win)

### Setup

```bash
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
```

### Usage

```bash
python auction_client.py --base-url http://localhost:8080 --item-id <ITEM_UUID> --user-id <USER_UUID> --max-bid 50
```

Print-only (no auto-bid):

```bash
python auction_client.py --base-url http://localhost:8080 --item-id <ITEM_UUID>
```

