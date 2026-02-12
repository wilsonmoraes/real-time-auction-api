package com.grepr.takehome.auction.service;

import com.grepr.takehome.auction.domain.User;
import com.grepr.takehome.auction.repo.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {
  private final Clock clock;
  private final UserRepository userRepository;

  public UserService(Clock clock, UserRepository userRepository) {
    this.clock = clock;
    this.userRepository = userRepository;
  }

  @Transactional
  public User create(String displayName) {
    if (StringUtils.isBlank(displayName)) {
      throw new BadRequestException("displayName is required");
    }
    User user = new User(UUID.randomUUID(), StringUtils.trim(displayName), Instant.now(clock));
    return userRepository.save(user);
  }

  @Transactional(readOnly = true)
  public User get(UUID userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException("User not found: " + userId));
  }
}

