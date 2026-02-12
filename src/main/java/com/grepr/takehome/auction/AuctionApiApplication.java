package com.grepr.takehome.auction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AuctionApiApplication {
  static void main(String[] args) {
    SpringApplication.run(AuctionApiApplication.class, args);
  }
}

