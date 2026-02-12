package com.grepr.takehome.auction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bids")
public class Bid {
  @Id
  private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "auction_id", nullable = false)
  private Auction auction;

  @Column(name = "bidder_user_id", nullable = false)
  private UUID bidderUserId;

  @Column(name = "amount", nullable = false, precision = 19, scale = 2)
  private BigDecimal amount;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Bid() {}

  public Bid(UUID id, Auction auction, UUID bidderUserId, BigDecimal amount, Instant createdAt) {
    this.id = id;
    this.auction = auction;
    this.bidderUserId = bidderUserId;
    this.amount = amount;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public Auction getAuction() {
    return auction;
  }

  public UUID getBidderUserId() {
    return bidderUserId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}

