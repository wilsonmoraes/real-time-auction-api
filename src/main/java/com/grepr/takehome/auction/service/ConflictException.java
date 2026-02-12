package com.grepr.takehome.auction.service;

public class ConflictException extends RuntimeException {
  public ConflictException(String message) {
    super(message);
  }
}

