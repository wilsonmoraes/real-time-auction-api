package com.grepr.takehome.auction.repo;

import com.grepr.takehome.auction.domain.Item;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemRepository extends JpaRepository<Item, UUID> {}

