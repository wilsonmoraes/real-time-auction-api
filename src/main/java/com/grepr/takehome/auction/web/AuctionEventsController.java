package com.grepr.takehome.auction.web;

import com.grepr.takehome.auction.domain.Auction;
import com.grepr.takehome.auction.realtime.AuctionEventMapper;
import com.grepr.takehome.auction.realtime.AuctionEventPublisher;
import com.grepr.takehome.auction.repo.AuctionRepository;
import com.grepr.takehome.auction.repo.ItemRepository;
import com.grepr.takehome.auction.service.NotFoundException;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/items/{itemId}/events")
public class AuctionEventsController {
  private final Clock clock;
  private final ItemRepository itemRepository;
  private final AuctionRepository auctionRepository;
  private final AuctionEventPublisher publisher;
  private final AuctionEventMapper mapper;

  public AuctionEventsController(
      Clock clock,
      ItemRepository itemRepository,
      AuctionRepository auctionRepository,
      AuctionEventPublisher publisher,
      AuctionEventMapper mapper
  ) {
    this.clock = clock;
    this.itemRepository = itemRepository;
    this.auctionRepository = auctionRepository;
    this.publisher = publisher;
    this.mapper = mapper;
  }

  @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter subscribe(@PathVariable UUID itemId) throws IOException {
    itemRepository.findById(itemId)
        .orElseThrow(() -> new NotFoundException("Item not found: " + itemId));

    Instant now = Instant.now(clock);
    Auction auction = auctionRepository.findByItem_Id(itemId).orElse(null);

    SseEmitter emitter = publisher.subscribe(itemId);
    // Send an initial snapshot so the client can start without polling.
    emitter.send(SseEmitter.event()
        .name("SNAPSHOT")
        .data(mapper.snapshot(itemId, auction, now), MediaType.APPLICATION_JSON));

    return emitter;
  }
}

