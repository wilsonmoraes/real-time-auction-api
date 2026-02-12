package com.grepr.takehome.auction.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.grepr.takehome.auction.domain.Auction;
import com.grepr.takehome.auction.realtime.AuctionEvent;
import com.grepr.takehome.auction.realtime.AuctionEventMapper;
import com.grepr.takehome.auction.realtime.AuctionEventPublisher;
import com.grepr.takehome.auction.repo.AuctionRepository;
import com.grepr.takehome.auction.repo.ItemRepository;
import com.grepr.takehome.auction.service.AuctionService;
import com.grepr.takehome.auction.service.BadRequestException;
import com.grepr.takehome.auction.service.NotFoundException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class AuctionWebSocketHandler extends TextWebSocketHandler {
  private static final String ATTR_ITEM_ID = "itemId";

  private final Clock clock;
  private final ObjectMapper objectMapper;
  private final ItemRepository itemRepository;
  private final AuctionRepository auctionRepository;
  private final AuctionService auctionService;
  private final AuctionEventPublisher publisher;
  private final AuctionEventMapper mapper;

  public AuctionWebSocketHandler(
      Clock clock,
      ObjectMapper objectMapper,
      ItemRepository itemRepository,
      AuctionRepository auctionRepository,
      AuctionService auctionService,
      AuctionEventPublisher publisher,
      AuctionEventMapper mapper
  ) {
    this.clock = clock;
    this.objectMapper = objectMapper;
    this.itemRepository = itemRepository;
    this.auctionRepository = auctionRepository;
    this.auctionService = auctionService;
    this.publisher = publisher;
    this.mapper = mapper;
  }

  @Override
  public void handleTextMessage(@NonNull WebSocketSession session, TextMessage message) throws Exception {
    JsonNode root = objectMapper.readTree(message.getPayload());
    String type = text(root, "type");
    if (type == null) {
      sendError(session, "Missing 'type'");
      return;
    }

    switch (type) {
      case "SUBSCRIBE" -> handleSubscribe(session, root);
      case "PLACE_BID" -> handlePlaceBid(session, root);
      default -> sendError(session, "Unknown message type: " + type);
    }
  }

  private void handleSubscribe(WebSocketSession session, JsonNode root) throws Exception {
    UUID itemId = uuid(root, "itemId");
    if (itemId == null) {
      sendError(session, "Missing/invalid 'itemId'");
      return;
    }

    itemRepository.findById(itemId)
        .orElseThrow(() -> new NotFoundException("Item not found: " + itemId));

    // Single subscription per session (simple).
    UUID previous = (UUID) session.getAttributes().get(ATTR_ITEM_ID);
    if (previous != null && !previous.equals(itemId)) {
      publisher.removeSession(previous, session);
    }
    session.getAttributes().put(ATTR_ITEM_ID, itemId);
    publisher.addSession(itemId, session);

    // Initial snapshot (sent only to this session).
    Instant now = Instant.now(clock);
    Auction auction = auctionRepository.findByItem_Id(itemId).orElse(null);
    AuctionEvent snapshot = mapper.snapshot(itemId, auction, now);
    session.sendMessage(new TextMessage(serialize(snapshot)));
  }

  private void handlePlaceBid(WebSocketSession session, JsonNode root) throws Exception {
    UUID itemId = uuid(root, "itemId");
    UUID bidderUserId = uuid(root, "bidderUserId");
    BigDecimal amount = decimal(root);

    if (itemId == null || bidderUserId == null || amount == null) {
      sendError(session, "Required: itemId, bidderUserId, amount");
      return;
    }

    try {
      auctionService.placeBid(itemId, bidderUserId, amount);
      // Success event is broadcast by AuctionService via publisher.
    } catch (BadRequestException | NotFoundException ex) {
      sendError(session, ex.getMessage());
    } catch (Exception ex) {
      sendError(session, "Failed to place bid");
    }
  }

  @Override
  public void afterConnectionClosed(WebSocketSession session, @NonNull CloseStatus status) {
    UUID itemId = (UUID) session.getAttributes().get(ATTR_ITEM_ID);
    if (itemId != null) {
      publisher.removeSession(itemId, session);
    }
  }

  private void sendError(WebSocketSession session, String message) throws Exception {
    session.sendMessage(new TextMessage(serialize(new ErrorMessage("ERROR", message))));
  }

  private record ErrorMessage(String type, String message) {}
  
  private String serialize(Object value) {
    try {
      return objectMapper.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize WebSocket message", e);
    }
  }

  private static String text(JsonNode root, String field) {
    JsonNode node = root.get(field);
    return node == null || node.isNull() ? null : node.asText(null);
  }

  private static UUID uuid(JsonNode root, String field) {
    String raw = text(root, field);
    if (raw == null || raw.isBlank()) {
      return null;
    }
    try {
      return UUID.fromString(raw);
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static BigDecimal decimal(JsonNode root) {
    JsonNode node = root.get("amount");
    if (node == null || node.isNull()) {
      return null;
    }
    try {
      if (node.isNumber()) {
        return node.decimalValue();
      }
      if (node.isTextual()) {
        return new BigDecimal(node.asText());
      }
      return null;
    } catch (Exception e) {
      return null;
    }
  }
}

