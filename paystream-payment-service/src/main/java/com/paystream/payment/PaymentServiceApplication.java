package com.paystream.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Payment Service — Milestone 3.
 * Adds Kafka outbox relay for async event publishing.
 * payment-service → wallet-service remains synchronous REST.
 * Ledger entries are now written asynchronously via wallet events consumed by ledger-service.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.paystream.payment.infrastructure.external")
@EnableScheduling
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
