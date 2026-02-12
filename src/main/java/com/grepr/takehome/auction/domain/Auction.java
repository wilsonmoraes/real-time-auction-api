package com.grepr.takehome.auction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auctions")
public class Auction {
  @Id
  private UUID id;

  @OneToOne(optional = false)
  @JoinColumn(name = "item_id", nullable = false, unique = true)
  private Item item;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private AuctionStatus status;

  @Column(name = "start_time", nullable = false)
  private Instant startTime;

  @Column(name = "end_time", nullable = false)
  private Instant endTime;

  @Column(name = "starting_price", nullable = false, precision = 19, scale = 2)
  private BigDecimal startingPrice;

  @Column(name = "min_increment", nullable = false, precision = 19, scale = 2)
  private BigDecimal minIncrement;

  @Column(name = "current_price", nullable = false, precision = 19, scale = 2)
  private BigDecimal currentPrice;

  @Column(name = "current_winner_user_id")
  private UUID currentWinnerUserId;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  protected Auction() {}

  public Auction(
      UUID id,
      Item item,
      AuctionStatus status,
      Instant startTime,
      Instant endTime,
      BigDecimal startingPrice,
      BigDecimal minIncrement,
      BigDecimal currentPrice,
      UUID currentWinnerUserId,
      Instant closedAt
  ) {
    this.id = id;
    this.item = item;
    this.status = status;
    this.startTime = startTime;
    this.endTime = endTime;
    this.startingPrice = startingPrice;
    this.minIncrement = minIncrement;
    this.currentPrice = currentPrice;
    this.currentWinnerUserId = currentWinnerUserId;
    this.closedAt = closedAt;
  }

  public UUID getId() {
    return id;
  }

  public Item getItem() {
    return item;
  }

  public AuctionStatus getStatus() {
    return status;
  }

  public Instant getStartTime() {
    return startTime;
  }

  public Instant getEndTime() {
    return endTime;
  }

  public BigDecimal getStartingPrice() {
    return startingPrice;
  }

  public BigDecimal getMinIncrement() {
    return minIncrement;
  }

  public BigDecimal getCurrentPrice() {
    return currentPrice;
  }

  public UUID getCurrentWinnerUserId() {
    return currentWinnerUserId;
  }

  public Instant getClosedAt() {
    return closedAt;
  }

  public AuctionStatus effectiveStatus(Instant now) {
    if (status == AuctionStatus.CLOSED) {
      return AuctionStatus.CLOSED;
    }
    if (now.isBefore(startTime)) {
      return AuctionStatus.SCHEDULED;
    }
    if (!now.isBefore(endTime)) {
      return AuctionStatus.CLOSED;
    }
    return AuctionStatus.OPEN;
  }

  public boolean refreshStatus(Instant now) {
    AuctionStatus effective = effectiveStatus(now);
    if (effective == status) {
      return false;
    }
    this.status = effective;
    if (effective == AuctionStatus.CLOSED) {
      this.closedAt = now;
    }
    return true;
  }

  public void applyWinningBid(UUID bidderUserId, BigDecimal amount) {
    this.currentWinnerUserId = bidderUserId;
    this.currentPrice = amount;
  }
}

