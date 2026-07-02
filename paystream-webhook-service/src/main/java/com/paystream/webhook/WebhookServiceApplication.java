package com.paystream.webhook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * Webhook Service — Milestone 3.
 * Delivers signed webhooks to merchant endpoints.
 * HMAC-SHA256 signature with timestamp anti-replay protection.
 * Retry: 5 attempts with exponential backoff (1m → 6h).
 * 4xx → EXHAUSTED immediately. 5xx/timeout → Kafka retry.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableKafka
public class WebhookServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebhookServiceApplication.class, args);
    }
}
