package com.grepr.takehome.auction.service;

public class BadRequestException extends RuntimeException {
  public BadRequestException(String message) {
    super(message);
  }
}

