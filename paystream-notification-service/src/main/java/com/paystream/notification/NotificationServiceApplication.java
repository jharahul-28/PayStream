package com.paystream.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Notification Service — Milestone 3.
 * Consumes payment.completed and payment.failed events.
 * Dispatches email (via Mailhog in local dev), stubs SMS and push.
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableKafka
@EnableScheduling
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
