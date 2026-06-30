package com.paystream.ledger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Ledger Service — Milestone 3.
 * Consumes WalletDebited/WalletCredited Kafka events to write ledger entries asynchronously.
 * REST inbound for double-entry creation (POST /ledger/entries) is retained for backward compatibility.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableKafka
public class LedgerServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(LedgerServiceApplication.class, args);
    }
}
