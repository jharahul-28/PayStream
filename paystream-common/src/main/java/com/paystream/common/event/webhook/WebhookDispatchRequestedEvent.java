package com.paystream.common.event.webhook;

public record WebhookDispatchRequestedEvent(
        String deliveryId,
        String merchantId,
        String endpointId,
        String url,
        String secret,
        String eventType,
        String payloadJson,
        String correlationId
) {}
