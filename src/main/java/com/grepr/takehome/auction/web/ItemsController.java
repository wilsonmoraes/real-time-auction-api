package com.grepr.takehome.auction.web;

import com.grepr.takehome.auction.domain.Auction;
import com.grepr.takehome.auction.domain.Bid;
import com.grepr.takehome.auction.domain.Item;
import com.grepr.takehome.auction.exception.NotFoundException;
import com.grepr.takehome.auction.repo.AuctionRepository;
import com.grepr.takehome.auction.repo.BidRepository;
import com.grepr.takehome.auction.repo.ItemRepository;
import com.grepr.takehome.auction.service.AuctionService;
import com.grepr.takehome.auction.service.ItemService;
import com.grepr.takehome.auction.web.dto.ItemDtos.AuctionResponse;
import com.grepr.takehome.auction.web.dto.ItemDtos.BidResponse;
import com.grepr.takehome.auction.web.dto.ItemDtos.BidsPageResponse;
import com.grepr.takehome.auction.web.dto.ItemDtos.CreateItemRequest;
import com.grepr.takehome.auction.web.dto.ItemDtos.ItemsPageResponse;
import com.grepr.takehome.auction.web.dto.ItemDtos.ItemResponse;
import com.grepr.takehome.auction.web.dto.ItemDtos.PlaceBidRequest;
import com.grepr.takehome.auction.web.dto.ItemDtos.ScheduleAuctionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/items")
@Validated
public class ItemsController {
  private final Clock clock;
  private final ItemService itemService;
  private final AuctionService auctionService;
  private final ItemRepository itemRepository;
  private final AuctionRepository auctionRepository;
  private final BidRepository bidRepository;

  public ItemsController(
      Clock clock,
      ItemService itemService,
      AuctionService auctionService,
      ItemRepository itemRepository,
      AuctionRepository auctionRepository,
      BidRepository bidRepository
  ) {
    this.clock = clock;
    this.itemService = itemService;
    this.auctionService = auctionService;
    this.itemRepository = itemRepository;
    this.auctionRepository = auctionRepository;
    this.bidRepository = bidRepository;
  }

  @GetMapping
  public ItemsPageResponse list(
          @RequestParam(defaultValue = "0") @Min(0) int page,
          @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
  ) {
      PageRequest pageable = PageRequest.of(
            page,
            size,
            Sort.by(Sort.Direction.DESC, "createdAt")
    );

      Page<ItemResponse> itemsPage = itemRepository.listItems(pageable);

    return new ItemsPageResponse(
            itemsPage.getContent(),
            itemsPage.getNumber(),
            itemsPage.getSize(),
            itemsPage.getTotalElements(),
            itemsPage.getTotalPages()
    );
  }

  @GetMapping("/{itemId}")
  public ItemResponse get(@PathVariable UUID itemId) {
    return itemRepository.findItemResponseById(itemId)
        .orElseThrow(() -> new NotFoundException("Item not found: " + itemId));
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public ItemResponse create(@Valid @RequestBody CreateItemRequest request) {
    Item item = itemService.create(request.name(), request.description());
    return new ItemResponse(item.getId(), item.getName(), item.getDescription(), item.getCreatedAt(), null);
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
  public BidsPageResponse listBids(
      @PathVariable UUID itemId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(200) int size
  ) {

    Auction auction = auctionRepository.findByItem_Id(itemId)
        .orElseThrow(() -> new NotFoundException("Auction not found for item: " + itemId));

      PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
      Page<BidResponse> bidsPage = bidRepository.findBidResponsesByAuctionId(auction.getId(), pageable);

    return new BidsPageResponse(
        bidsPage.getContent(),
        bidsPage.getNumber(),
        bidsPage.getSize(),
        bidsPage.getTotalElements(),
        bidsPage.getTotalPages()
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

