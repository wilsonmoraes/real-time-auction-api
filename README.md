# Real-Time Item Auction API

Backend service that simulates a live auction for unique items. Users can view items up for auction and place bids within a timeframe. The system handles competing bids and determines a winner when the auction closes.

## Quick start (Docker)

Start Postgres + API:

```bash
docker compose up --build
```

Then open:
- API base: `http://localhost:8080/api`
- Swagger UI: `http://localhost:8080/swagger-ui`
- Prometheus metrics: `http://localhost:8080/actuator/prometheus`
- Spring Boot Admin UI: `http://localhost:8080/admin`
- Prometheus UI: `http://localhost:9090`
- Grafana UI: `http://localhost:3000` (admin/admin)
- Loki API: `http://localhost:3100`

## Prometheus (PromQL quick queries)

Copy/paste into the Prometheus UI at `http://localhost:9090`.

- **Bid accepted rate (per second)**:
  - `rate(auction_bids_accepted_total[1m])`
- **Bid rejected rate by reason (per second)**:
  - `sum by (reason) (rate(auction_bids_rejected_total[1m]))`
- **Total bid rejections by reason**:
  - `sum by (reason) (auction_bids_rejected_total)`
- **HTTP request rate by route and status**:
  - `sum by (uri, status) (rate(http_server_requests_seconds_count[1m]))`
- **HTTP latency p95 by route (5m window)**:
  - `histogram_quantile(0.95, sum by (le, uri) (rate(http_server_requests_seconds_bucket[5m])))`
- **HTTP latency average by route (5m window)**:
  - `sum by (uri) (rate(http_server_requests_seconds_sum[5m])) / sum by (uri) (rate(http_server_requests_seconds_count[5m]))`

## Logs in Grafana (Loki)

After `docker compose up --build`, logs from the `api` container are shipped to Loki by Promtail.

- Open Grafana: `http://localhost:3000` (admin/admin)
- Dashboard: **"Auction API Logs"**
- Or use Explore with the query:
  - `{compose_service="api"}`

## Real-time (WebSocket)

Connect a WebSocket client to:

`ws://localhost:8080/ws`

Send:
- Subscribe:
  - `{ "type": "SUBSCRIBE", "itemId": "<ITEM_UUID>" }`
- Place bid (bidirectional interaction):
  - `{ "type": "PLACE_BID", "itemId": "<ITEM_UUID>", "bidderUserId": "<USER_UUID>", "amount": 12.00 }`

The server sends JSON events:
- `SNAPSHOT` (initial state)
- `AUCTION_SCHEDULED`, `AUCTION_OPENED`, `AUCTION_CLOSED`
- `BID_PLACED`

## Bonus CLI client (Python)

See `automation/` folder.

## Local run (requires Postgres)

Create a Postgres DB (defaults below), then:

```bash
mvn -DskipTests package
mvn spring-boot:run
```

Defaults (override via env vars):
- DB: `jdbc:postgresql://localhost:5432/auction`
- user/pass: `auction` / `auction`

## API overview

### Create a user

`POST /api/users`

```json
{ "displayName": "alice" }
```

### Create an item

`POST /api/items`

```json
{ "name": "Vintage Watch", "description": "Unique item" }
```

### Schedule an auction for an item

`POST /api/items/{itemId}/auction`

```json
{
  "startTime": "2026-02-12T20:00:00Z",
  "endTime": "2026-02-12T20:05:00Z",
  "startingPrice": 10.00,
  "minIncrement": 1.00
}
```

Auction states:
- `SCHEDULED`: now < startTime
- `OPEN`: startTime <= now < endTime
- `CLOSED`: now >= endTime

### Place a bid

`POST /api/items/{itemId}/bids`

```json
{ "bidderUserId": "<uuid>", "amount": 12.00 }
```

Rules:
- bids only accepted when auction is `OPEN`
- bid must be at least `currentPrice + minIncrement`

### View items / item details

- `GET /api/items?page=0&size=20` (paginated, ordered by `createdAt desc`)
- `GET /api/items/{itemId}`

Both return an `auctionId` (nullable). To fetch auction details:

- `GET /api/auctions/{auctionId}`

### View bids

`GET /api/items/{itemId}/bids`