package com.grepr.takehome.auction.observability;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Centralized metric names and labels for the Auction API.
 *
 * <p>Keep metric names stable: changing them breaks dashboards/alerts.
 */
public final class AuctionMetrics {
  private AuctionMetrics() {}

  public static final String BID_ACCEPTED_COUNTER = "auction.bids.accepted";
  public static final String BID_REJECTED_COUNTER = "auction.bids.rejected";

  public static final String REASON_TAG = "reason";

  public enum BidRejectedReason {
    INVALID_AMOUNT("invalid_amount"),
    UNKNOWN_USER("unknown_user"),
    AUCTION_NOT_OPEN("auction_not_open"),
    BID_TOO_LOW("bid_too_low");

    private final String tagValue;

    BidRejectedReason(String tagValue) {
      this.tagValue = tagValue;
    }

    public String tagValue() {
      return tagValue;
    }
  }

  public static void incrementBidAccepted(MeterRegistry registry) {
    registry.counter(BID_ACCEPTED_COUNTER).increment();
  }

  public static void incrementBidRejected(MeterRegistry registry, BidRejectedReason reason) {
    registry.counter(BID_REJECTED_COUNTER, REASON_TAG, reason.tagValue()).increment();
  }
}

