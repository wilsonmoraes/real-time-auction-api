CREATE TABLE users (
  id UUID PRIMARY KEY,
  display_name TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE items (
  id UUID PRIMARY KEY,
  name TEXT NOT NULL,
  description TEXT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE auctions (
  id UUID PRIMARY KEY,
  item_id UUID NOT NULL UNIQUE REFERENCES items(id) ON DELETE CASCADE,
  status TEXT NOT NULL,
  start_time TIMESTAMPTZ NOT NULL,
  end_time TIMESTAMPTZ NOT NULL,
  starting_price NUMERIC(19, 2) NOT NULL,
  min_increment NUMERIC(19, 2) NOT NULL,
  current_price NUMERIC(19, 2) NOT NULL,
  current_winner_user_id UUID NULL,
  closed_at TIMESTAMPTZ NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT auctions_time_order CHECK (end_time > start_time),
  CONSTRAINT auctions_status_chk CHECK (status IN ('SCHEDULED', 'OPEN', 'CLOSED')),
  CONSTRAINT auctions_price_chk CHECK (starting_price >= 0 AND min_increment > 0 AND current_price >= 0)
);

CREATE INDEX auctions_status_start_time_idx ON auctions(status, start_time);
CREATE INDEX auctions_status_end_time_idx ON auctions(status, end_time);

CREATE TABLE bids (
  id UUID PRIMARY KEY,
  auction_id UUID NOT NULL REFERENCES auctions(id) ON DELETE CASCADE,
  bidder_user_id UUID NOT NULL,
  amount NUMERIC(19, 2) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT bids_amount_chk CHECK (amount > 0)
);

CREATE INDEX bids_auction_created_at_idx ON bids(auction_id, created_at DESC);
CREATE INDEX bids_auction_amount_idx ON bids(auction_id, amount DESC);
