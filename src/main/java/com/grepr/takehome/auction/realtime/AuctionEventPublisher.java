package com.grepr.takehome.auction.realtime;

import java.io.IOException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Component
public class AuctionEventPublisher {
  private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByItemId = new ConcurrentHashMap<>();

  public SseEmitter subscribe(UUID itemId) {
    // Keep connections open; proxies/clients may still timeout. We'll rely on reconnection.
    SseEmitter emitter = new SseEmitter(Duration.ofMinutes(30).toMillis());
    emittersByItemId.computeIfAbsent(itemId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);

    emitter.onCompletion(() -> remove(itemId, emitter));
    emitter.onTimeout(() -> remove(itemId, emitter));
    emitter.onError(ignored -> remove(itemId, emitter));

    return emitter;
  }

  public void publish(UUID itemId, AuctionEvent event) {
    CopyOnWriteArrayList<SseEmitter> emitters = emittersByItemId.get(itemId);
    if (emitters == null || emitters.isEmpty()) {
      return;
    }

    for (SseEmitter emitter : emitters) {
      try {
        emitter.send(SseEmitter.event()
            .name(event.type().name())
            .data(event, MediaType.APPLICATION_JSON));
      } catch (IOException | IllegalStateException ex) {
        remove(itemId, emitter);
      }
    }
  }

  private void remove(UUID itemId, SseEmitter emitter) {
    CopyOnWriteArrayList<SseEmitter> emitters = emittersByItemId.get(itemId);
    if (emitters == null) {
      return;
    }
    emitters.remove(emitter);
    if (emitters.isEmpty()) {
      emittersByItemId.remove(itemId, emitters);
    }
  }
}

