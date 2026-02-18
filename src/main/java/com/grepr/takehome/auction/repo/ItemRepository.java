package com.grepr.takehome.auction.repo;

import com.grepr.takehome.auction.domain.Item;
import com.grepr.takehome.auction.web.dto.ItemDtos;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface ItemRepository extends JpaRepository<Item, UUID> {

  @Query("""
      select new com.grepr.takehome.auction.web.dto.ItemDtos$ItemResponse(
        i.id, i.name, i.description, i.createdAt, a.id
      )
      from Item i
      left join Auction a on a.item = i
      order by i.createdAt desc
      """)
  Page<ItemDtos.ItemResponse> listItems(Pageable pageable);

  @Query("""
      select new com.grepr.takehome.auction.web.dto.ItemDtos$ItemResponse(
        i.id, i.name, i.description, i.createdAt, a.id
      )
      from Item i
      left join Auction a on a.item = i
      where i.id = :itemId
      """)
  Optional<ItemDtos.ItemResponse> findItemResponseById(UUID itemId);
}

