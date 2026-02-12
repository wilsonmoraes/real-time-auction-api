package com.grepr.takehome.auction.web;

import com.grepr.takehome.auction.domain.Auction;
import com.grepr.takehome.auction.domain.Bid;
import com.grepr.takehome.auction.domain.Item;
import com.grepr.takehome.auction.repo.AuctionRepository;
import com.grepr.takehome.auction.repo.ItemRepository;
import com.grepr.takehome.auction.service.AuctionService;
import com.grepr.takehome.auction.service.ItemService;
import com.grepr.takehome.auction.web.dto.ItemDtos.AuctionResponse;
import com.grepr.takehome.auction.web.dto.ItemDtos.BidResponse;
import com.grepr.takehome.auction.web.dto.ItemDtos.CreateItemRequest;
import com.grepr.takehome.auction.web.dto.ItemDtos.ItemResponse;
import com.grepr.takehome.auction.web.dto.ItemDtos.PlaceBidRequest;
import com.grepr.takehome.auction.web.dto.ItemDtos.ScheduleAuctionRequest;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/items")
public class ItemsController {
  private final Clock clock;
  private final ItemService itemService;
  private final AuctionService auctionService;
  private final ItemRepository itemRepository;
  private final AuctionRepository auctionRepository;

  public ItemsController(
      Clock clock,
      ItemService itemService,
      AuctionService auctionService,
      ItemRepository itemRepository,
      AuctionRepository auctionRepository
  ) {
    this.clock = clock;
    this.itemService = itemService;
    this.auctionService = auctionService;
    this.itemRepository = itemRepository;
    this.auctionRepository = auctionRepository;
  }

  @GetMapping
  public List<ItemResponse> list() {
    Instant now = Instant.now(clock);
    Map<UUID, Auction> auctionsByItemId = auctionRepository.findAll().stream()
        .collect(Collectors.toMap(a -> a.getItem().getId(), Function.identity()));

    return itemRepository.findAll().stream()
        .sorted(Comparator.comparing(Item::getCreatedAt).reversed())
        .map(item -> toResponse(item, auctionsByItemId.get(item.getId()), now))
        .toList();
  }

  @GetMapping("/{itemId}")
  public ItemResponse get(@PathVariable UUID itemId) {
    Instant now = Instant.now(clock);
    Item item = itemService.get(itemId);
    Auction auction = auctionRepository.findByItem_Id(itemId).orElse(null);
    return toResponse(item, auction, now);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ItemResponse create(@Valid @RequestBody CreateItemRequest request) {
    Instant now = Instant.now(clock);
    Item item = itemService.create(request.name(), request.description());
    return toResponse(item, null, now);
  }

  @PostMapping("/{itemId}/auction")
  @ResponseStatus(HttpStatus.CREATED)
  public AuctionResponse scheduleAuction(
      @PathVariable UUID itemId,
      @Valid @RequestBody ScheduleAuctionRequest request
  ) {
    Auction auction = auctionService.scheduleAuction(
        itemId,
        request.startTime(),
        request.endTime(),
        request.startingPrice(),
        request.minIncrement()
    );
    Instant now = Instant.now(clock);
    return toAuctionResponse(auction, now);
  }

  @PostMapping("/{itemId}/bids")
  @ResponseStatus(HttpStatus.CREATED)
  public BidResponse placeBid(@PathVariable UUID itemId, @Valid @RequestBody PlaceBidRequest request) {
    Bid bid = auctionService.placeBid(itemId, request.bidderUserId(), request.amount());
    return new BidResponse(bid.getId(), bid.getAuction().getId(), bid.getBidderUserId(), bid.getAmount(), bid.getCreatedAt());
  }

  @GetMapping("/{itemId}/bids")
  public List<BidResponse> listBids(@PathVariable UUID itemId) {
    List<Bid> bids = auctionService.listBidsForItem(itemId);
    return bids.stream()
        .map(b -> new BidResponse(b.getId(), b.getAuction().getId(), b.getBidderUserId(), b.getAmount(), b.getCreatedAt()))
        .toList();
  }

  private ItemResponse toResponse(Item item, Auction auction, Instant now) {
    return new ItemResponse(
        item.getId(),
        item.getName(),
        item.getDescription(),
        item.getCreatedAt(),
        auction == null ? null : toAuctionResponse(auction, now)
    );
  }

  private AuctionResponse toAuctionResponse(Auction auction, Instant now) {
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

