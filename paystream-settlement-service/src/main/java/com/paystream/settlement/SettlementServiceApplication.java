package com.paystream.settlement;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Settlement Service — Milestone 3.
 * Runs daily at 02:00 IST to batch and settle merchant payments.
 * Micro-batch processing (100 items per commit) for checkpoint resilience.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
public class SettlementServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SettlementServiceApplication.class, args);
    }
}
