## Automation (Python)

This folder contains simple scripts to support the demo:

- `client/auction_client.py`: WebSocket client that prints real-time events and can auto-bid up to a max limit (and also allows manual bids from the console).
- `hammer/hammer_bids.py`: concurrency demo that creates a user + item + auction and then fires many concurrent bids to validate pessimistic locking behavior.

### Setup

```bash
python -m venv .venv
.\.venv\Scripts\activate
pip install -r requirements.txt
```

### Run

Open two terminals:

1) WebSocket client:

```bash
python client/auction_client.py
```

2) Hammer:

```bash
python hammer/hammer_bids.py
```

