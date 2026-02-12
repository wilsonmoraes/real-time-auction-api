package com.grepr.takehome.auction.scheduler;

import com.grepr.takehome.auction.domain.Auction;
import com.grepr.takehome.auction.domain.AuctionStatus;
import com.grepr.takehome.auction.realtime.AuctionEventMapper;
import com.grepr.takehome.auction.realtime.AuctionEventPublisher;
import com.grepr.takehome.auction.realtime.AuctionEventType;
import com.grepr.takehome.auction.repo.AuctionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class AuctionStateScheduler {
  private final Clock clock;
  private final AuctionRepository auctionRepository;
  private final AuctionEventPublisher eventPublisher;
  private final AuctionEventMapper eventMapper;

  public AuctionStateScheduler(
      Clock clock,
      AuctionRepository auctionRepository,
      AuctionEventPublisher eventPublisher,
      AuctionEventMapper eventMapper
  ) {
    this.clock = clock;
    this.auctionRepository = auctionRepository;
    this.eventPublisher = eventPublisher;
    this.eventMapper = eventMapper;
  }

  @Scheduled(fixedDelayString = "${auction.scheduler.delay-ms:1000}")
  @Transactional
  public void refreshAuctionStates() {
    Instant now = Instant.now(clock);

    List<Auction> toOpen = auctionRepository.findScheduledToOpen(AuctionStatus.SCHEDULED, now);
    for (Auction auction : toOpen) {
      boolean changed = auction.refreshStatus(now);
      Auction saved = auctionRepository.save(auction);
      if (changed && saved.getStatus() == AuctionStatus.OPEN) {
        eventPublisher.publish(saved.getItem().getId(), eventMapper.auctionEvent(AuctionEventType.AUCTION_OPENED, saved.getItem().getId(), saved, now));
      }
    }

    List<Auction> toClose = auctionRepository.findOpenToClose(AuctionStatus.OPEN, now);
    for (Auction auction : toClose) {
      boolean changed = auction.refreshStatus(now);
      Auction saved = auctionRepository.save(auction);
      if (changed && saved.getStatus() == AuctionStatus.CLOSED) {
        eventPublisher.publish(saved.getItem().getId(), eventMapper.auctionEvent(AuctionEventType.AUCTION_CLOSED, saved.getItem().getId(), saved, now));
      }
    }
  }
}

