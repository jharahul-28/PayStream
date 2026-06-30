package com.paystream.wallet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Wallet Service — Milestone 1 skeleton.
 * Full implementation in Milestone 2 (wallet CRUD, debit/credit, optimistic locking).
 */
@SpringBootApplication
@EnableDiscoveryClient
public class WalletServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WalletServiceApplication.class, args);
    }
}
