package com.grepr.takehome.auction.realtime;

import com.grepr.takehome.auction.domain.AuctionStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AuctionEvent(
    AuctionEventType type,
    Instant timestamp,
    UUID itemId,
    AuctionPayload auction,
    BidPayload bid
) {
  public record AuctionPayload(
      UUID id,
      AuctionStatus status,
      Instant startTime,
      Instant endTime,
      BigDecimal startingPrice,
      BigDecimal minIncrement,
      BigDecimal currentPrice,
      UUID currentWinnerUserId,
      Instant closedAt
  ) {}

  public record BidPayload(
      UUID id,
      UUID auctionId,
      UUID bidderUserId,
      BigDecimal amount,
      Instant createdAt
  ) {}
}

