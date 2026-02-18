package com.grepr.takehome.auction.repo;

import com.grepr.takehome.auction.domain.Bid;
import com.grepr.takehome.auction.web.dto.ItemDtos;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BidRepository extends JpaRepository<Bid, UUID> {
  List<Bid> findByAuction_IdOrderByCreatedAtDesc(UUID auctionId);

  @Query("""
      select new com.grepr.takehome.auction.web.dto.ItemDtos$BidResponse(
        b.id, b.auction.id, b.bidderUserId, b.amount, b.createdAt
      )
      from Bid b
      where b.auction.id = :auctionId
      order by b.createdAt desc
      """)
  Page<ItemDtos.BidResponse> findBidResponsesByAuctionId(@Param("auctionId") UUID auctionId, Pageable pageable);
}

