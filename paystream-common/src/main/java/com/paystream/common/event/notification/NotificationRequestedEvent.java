package com.paystream.common.event.notification;

public record NotificationRequestedEvent(
        String notificationId,
        String userId,
        String paymentId,
        String type,       // EMAIL, SMS, PUSH
        String channel,    // PAYMENT_SUCCESS, PAYMENT_FAILED
        String recipient,
        String subject,
        String body,
        String correlationId
) {}
