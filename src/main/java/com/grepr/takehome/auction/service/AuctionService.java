package com.grepr.takehome.auction.service;

import com.grepr.takehome.auction.domain.Auction;
import com.grepr.takehome.auction.domain.AuctionStatus;
import com.grepr.takehome.auction.domain.Bid;
import com.grepr.takehome.auction.domain.Item;
import com.grepr.takehome.auction.realtime.AuctionEventMapper;
import com.grepr.takehome.auction.realtime.AuctionEventPublisher;
import com.grepr.takehome.auction.realtime.AuctionEventType;
import com.grepr.takehome.auction.repo.AuctionRepository;
import com.grepr.takehome.auction.repo.BidRepository;
import com.grepr.takehome.auction.repo.ItemRepository;
import com.grepr.takehome.auction.repo.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuctionService {
  private final Clock clock;
  private final ItemRepository itemRepository;
  private final AuctionRepository auctionRepository;
  private final BidRepository bidRepository;
  private final UserRepository userRepository;
  private final MeterRegistry meterRegistry;
  private final AuctionEventPublisher eventPublisher;
  private final AuctionEventMapper eventMapper;

  public AuctionService(
      Clock clock,
      ItemRepository itemRepository,
      AuctionRepository auctionRepository,
      BidRepository bidRepository,
      UserRepository userRepository,
      MeterRegistry meterRegistry,
      AuctionEventPublisher eventPublisher,
      AuctionEventMapper eventMapper
  ) {
    this.clock = clock;
    this.itemRepository = itemRepository;
    this.auctionRepository = auctionRepository;
    this.bidRepository = bidRepository;
    this.userRepository = userRepository;
    this.meterRegistry = meterRegistry;
    this.eventPublisher = eventPublisher;
    this.eventMapper = eventMapper;
  }

  @Transactional
  public Auction scheduleAuction(
      UUID itemId,
      Instant startTime,
      Instant endTime,
      BigDecimal startingPrice,
      BigDecimal minIncrement
  ) {
    Instant now = Instant.now(clock);
    if (!startTime.isAfter(now)) {
      throw new BadRequestException("startTime must be in the future");
    }
    if (!endTime.isAfter(startTime)) {
      throw new BadRequestException("endTime must be after startTime");
    }
    if (startingPrice.compareTo(BigDecimal.ZERO) < 0) {
      throw new BadRequestException("startingPrice must be >= 0");
    }
    if (minIncrement.compareTo(BigDecimal.ZERO) <= 0) {
      throw new BadRequestException("minIncrement must be > 0");
    }

    Item item = itemRepository.findById(itemId)
        .orElseThrow(() -> new NotFoundException("Item not found: " + itemId));

    if (auctionRepository.findByItem_Id(itemId).isPresent()) {
      throw new ConflictException("Item already has an auction: " + itemId);
    }

    Auction auction = new Auction(
        UUID.randomUUID(),
        item,
        AuctionStatus.SCHEDULED,
        startTime,
        endTime,
        startingPrice,
        minIncrement,
        startingPrice,
        null,
        null
    );

    Auction saved = auctionRepository.save(auction);
    eventPublisher.publish(itemId, eventMapper.auctionEvent(AuctionEventType.AUCTION_SCHEDULED, itemId, saved, now));
    return saved;
  }

  @Transactional(readOnly = true)
  public Auction getAuctionForItem(UUID itemId) {
    itemRepository.findById(itemId)
        .orElseThrow(() -> new NotFoundException("Item not found: " + itemId));
    return auctionRepository.findByItem_Id(itemId)
        .orElseThrow(() -> new NotFoundException("Auction not found for item: " + itemId));
  }

  @Transactional
  public Bid placeBid(UUID itemId, UUID bidderUserId, BigDecimal amount) {
    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
      meterRegistry.counter("auction.bids.rejected", "reason", "invalid_amount").increment();
      throw new BadRequestException("amount must be > 0");
    }

    // Ensure item exists for clearer error messages.
    itemRepository.findById(itemId)
        .orElseThrow(() -> new NotFoundException("Item not found: " + itemId));

    if (!userRepository.existsById(bidderUserId)) {
      meterRegistry.counter("auction.bids.rejected", "reason", "unknown_user").increment();
      throw new BadRequestException("Unknown user: " + bidderUserId);
    }

    Auction auction = auctionRepository.findByItemIdForUpdate(itemId)
        .orElseThrow(() -> new NotFoundException("Auction not found for item: " + itemId));

    Instant now = Instant.now(clock);
    auction.refreshStatus(now);

    if (auction.getStatus() != AuctionStatus.OPEN) {
      meterRegistry.counter("auction.bids.rejected", "reason", "auction_not_open").increment();
      throw new BadRequestException("Auction is not open (status=" + auction.getStatus() + ")");
    }

    BigDecimal minAllowed = auction.getCurrentPrice().add(auction.getMinIncrement());
    if (amount.compareTo(minAllowed) < 0) {
      meterRegistry.counter("auction.bids.rejected", "reason", "bid_too_low").increment();
      throw new BadRequestException("Bid too low. Minimum allowed is " + minAllowed);
    }

    auction.applyWinningBid(bidderUserId, amount);
    Bid bid = new Bid(UUID.randomUUID(), auction, bidderUserId, amount, now);

    auctionRepository.save(auction);
    bidRepository.save(bid);

    meterRegistry.counter("auction.bids.accepted").increment();
    eventPublisher.publish(itemId, eventMapper.bidPlaced(itemId, auction, bid, now));
    return bid;
  }

  @Transactional(readOnly = true)
  public List<Bid> listBidsForItem(UUID itemId) {
    Auction auction = getAuctionForItem(itemId);
    return bidRepository.findByAuction_IdOrderByCreatedAtDesc(auction.getId());
  }
}

