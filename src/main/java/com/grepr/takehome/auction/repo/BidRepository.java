package com.grepr.takehome.auction.repo;

import com.grepr.takehome.auction.domain.Bid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BidRepository extends JpaRepository<Bid, UUID> {
  List<Bid> findByAuction_IdOrderByCreatedAtDesc(UUID auctionId);
}

