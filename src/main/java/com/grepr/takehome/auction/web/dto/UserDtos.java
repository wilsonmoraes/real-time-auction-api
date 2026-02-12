package com.grepr.takehome.auction.web.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;

public final class UserDtos {
  private UserDtos() {}

  public record CreateUserRequest(@NotBlank String displayName) {}

  public record UserResponse(UUID id, String displayName, Instant createdAt) {}
}

