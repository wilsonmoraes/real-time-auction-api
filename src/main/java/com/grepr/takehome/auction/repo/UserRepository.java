package com.grepr.takehome.auction.repo;

import com.grepr.takehome.auction.domain.User;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {}

