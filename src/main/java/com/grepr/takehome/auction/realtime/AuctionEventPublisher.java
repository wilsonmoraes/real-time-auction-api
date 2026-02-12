package com.grepr.takehome.auction.realtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class AuctionEventPublisher {
  private final ObjectMapper objectMapper;
  private final ConcurrentHashMap<UUID, CopyOnWriteArraySet<WebSocketSession>> sessionsByItemId = new ConcurrentHashMap<>();

  public AuctionEventPublisher(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public void publish(UUID itemId, AuctionEvent event) {
    CopyOnWriteArraySet<WebSocketSession> sessions = sessionsByItemId.get(itemId);
    if (sessions == null || sessions.isEmpty()) {
      return;
    }

    String json;
    try {
      json = objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize auction event", e);
    }
    TextMessage message = new TextMessage(json);
    for (WebSocketSession session : sessions) {
      try {
        if (session.isOpen()) {
          session.sendMessage(message);
        } else {
          remove(itemId, session);
        }
      } catch (Exception ex) {
        remove(itemId, session);
      }
    }
  }

  public void addSession(UUID itemId, WebSocketSession session) {
    sessionsByItemId.computeIfAbsent(itemId, ignored -> new CopyOnWriteArraySet<>()).add(session);
  }

  public void removeSession(UUID itemId, WebSocketSession session) {
    remove(itemId, session);
  }

  private void remove(UUID itemId, WebSocketSession session) {
    CopyOnWriteArraySet<WebSocketSession> sessions = sessionsByItemId.get(itemId);
    if (sessions == null) {
      return;
    }
    sessions.remove(session);
    if (sessions.isEmpty()) {
      sessionsByItemId.remove(itemId, sessions);
    }
  }
}

