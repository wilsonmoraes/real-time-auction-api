package com.grepr.takehome.auction.web;

import com.grepr.takehome.auction.domain.User;
import com.grepr.takehome.auction.service.UserService;
import com.grepr.takehome.auction.web.dto.UserDtos.CreateUserRequest;
import com.grepr.takehome.auction.web.dto.UserDtos.UserResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UsersController {
  private final UserService userService;

  public UsersController(UserService userService) {
    this.userService = userService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public UserResponse create(@Valid @RequestBody CreateUserRequest request) {
    User user = userService.create(request.displayName());
    return new UserResponse(user.getId(), user.getDisplayName(), user.getCreatedAt());
  }

  @GetMapping("/{userId}")
  public UserResponse get(@PathVariable UUID userId) {
    User user = userService.get(userId);
    return new UserResponse(user.getId(), user.getDisplayName(), user.getCreatedAt());
  }
}

