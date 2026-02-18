package com.grepr.takehome.auction.web;

import com.grepr.takehome.auction.domain.Auction;
import com.grepr.takehome.auction.exception.NotFoundException;
import com.grepr.takehome.auction.repo.AuctionRepository;
import com.grepr.takehome.auction.web.dto.ItemDtos.AuctionResponse;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auctions")
public class AuctionsController {
  private final Clock clock;
  private final AuctionRepository auctionRepository;

  public AuctionsController(Clock clock, AuctionRepository auctionRepository) {
    this.clock = clock;
    this.auctionRepository = auctionRepository;
  }

  @GetMapping("/{auctionId}")
  public AuctionResponse get(@PathVariable UUID auctionId) {
    Auction auction = auctionRepository.findById(auctionId)
        .orElseThrow(() -> new NotFoundException("Auction not found: " + auctionId));

    Instant now = Instant.now(clock);
    return new AuctionResponse(
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

