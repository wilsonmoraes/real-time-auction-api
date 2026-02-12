package com.grepr.takehome.auction.repo;

import com.grepr.takehome.auction.domain.Auction;
import com.grepr.takehome.auction.domain.AuctionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface AuctionRepository extends JpaRepository<Auction, UUID> {
  Optional<Auction> findByItem_Id(UUID itemId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Auction a where a.item.id = :itemId")
  Optional<Auction> findByItemIdForUpdate(@Param("itemId") UUID itemId);

  @Query("select a from Auction a where a.status = :status and a.startTime <= :now")
  List<Auction> findScheduledToOpen(@Param("status") AuctionStatus status, @Param("now") Instant now);

  @Query("select a from Auction a where a.status = :status and a.endTime <= :now")
  List<Auction> findOpenToClose(@Param("status") AuctionStatus status, @Param("now") Instant now);
}

