package com.grepr.takehome.auction.service;

import com.grepr.takehome.auction.domain.Item;
import com.grepr.takehome.auction.repo.ItemRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItemService {
  private final Clock clock;
  private final ItemRepository itemRepository;

  public ItemService(Clock clock, ItemRepository itemRepository) {
    this.clock = clock;
    this.itemRepository = itemRepository;
  }

  @Transactional
  public Item create(String name, String description) {
    if (StringUtils.isBlank(name)) {
      throw new BadRequestException("name is required");
    }
    Item item = new Item(UUID.randomUUID(), StringUtils.trim(name), description, Instant.now(clock));
    return itemRepository.save(item);
  }

  @Transactional(readOnly = true)
  public Item get(UUID itemId) {
    return itemRepository.findById(itemId)
        .orElseThrow(() -> new NotFoundException("Item not found: " + itemId));
  }
}

