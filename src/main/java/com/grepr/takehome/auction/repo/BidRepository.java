package com.grepr.takehome.auction.repo;

import com.grepr.takehome.auction.domain.Bid;
import com.grepr.takehome.auction.web.dto.ItemDtos;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface BidRepository extends JpaRepository<Bid, UUID> {

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

