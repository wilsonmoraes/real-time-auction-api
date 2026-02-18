package com.grepr.takehome.auction.web.dto;

import com.grepr.takehome.auction.domain.AuctionStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ItemDtos {
  private ItemDtos() {}

  public record CreateItemRequest(@NotBlank String name, String description) {}

  public record ScheduleAuctionRequest(
      @NotNull Instant startTime,
      @NotNull Instant endTime,
      @NotNull BigDecimal startingPrice,
      @NotNull @Positive BigDecimal minIncrement
  ) {}

  public record PlaceBidRequest(
      @NotNull UUID bidderUserId,
      @NotNull @Positive BigDecimal amount
  ) {}

  public record ItemResponse(
      UUID id,
      String name,
      String description,
      Instant createdAt,
      UUID auctionId
  ) {}

  public record ItemsPageResponse(
      List<ItemResponse> items,
      int page,
      int size,
      long totalElements,
      int totalPages
  ) {}

  public record BidsPageResponse(
      List<BidResponse> bids,
      int page,
      int size,
      long totalElements,
      int totalPages
  ) {}

  public record AuctionResponse(
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

  public record BidResponse(
      UUID id,
      UUID auctionId,
      UUID bidderUserId,
      BigDecimal amount,
      Instant createdAt
  ) {}
}

