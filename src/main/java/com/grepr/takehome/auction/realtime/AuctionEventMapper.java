package com.grepr.takehome.auction.realtime;

import com.grepr.takehome.auction.domain.Auction;
import com.grepr.takehome.auction.domain.Bid;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AuctionEventMapper {
  private final Clock clock;

  public AuctionEventMapper(Clock clock) {
    this.clock = clock;
  }

  public AuctionEvent snapshot(UUID itemId, Auction auction, Instant now) {
    return new AuctionEvent(
        AuctionEventType.SNAPSHOT,
        Instant.now(clock),
        itemId,
        toAuctionPayload(auction, now),
        null
    );
  }

  public AuctionEvent auctionEvent(AuctionEventType type, UUID itemId, Auction auction, Instant now) {
    return new AuctionEvent(
        type,
        Instant.now(clock),
        itemId,
        toAuctionPayload(auction, now),
        null
    );
  }

  public AuctionEvent bidPlaced(UUID itemId, Auction auction, Bid bid, Instant now) {
    return new AuctionEvent(
        AuctionEventType.BID_PLACED,
        Instant.now(clock),
        itemId,
        toAuctionPayload(auction, now),
        new AuctionEvent.BidPayload(
            bid.getId(),
            bid.getAuction().getId(),
            bid.getBidderUserId(),
            bid.getAmount(),
            bid.getCreatedAt()
        )
    );
  }

  private AuctionEvent.AuctionPayload toAuctionPayload(Auction auction, Instant now) {
    if (auction == null) {
      return null;
    }
    return new AuctionEvent.AuctionPayload(
        auction.getId(),
        auction.effectiveStatus(now),
        auction.getStartTime(),
        auction.getEndTime(),
        auction.getStartingPrice(),
        auction.getMinIncrement(),
        auction.getCurrentPrice(),
        auction.getCurrentWinnerUserId(),
        auction.getClosedAt()
    );
  }
}

