package com.paystream.notification.infrastructure.config;

import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Notification service has no external-facing secured endpoints.
 * It only consumes Kafka events and sends notifications.
 * Security is handled at the API gateway layer.
 */
@Configuration
public class SecurityConfig {
    // Intentionally minimal — no REST security needed for a pure event consumer
}
