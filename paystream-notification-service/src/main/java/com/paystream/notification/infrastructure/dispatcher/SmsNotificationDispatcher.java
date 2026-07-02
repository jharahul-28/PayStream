package com.paystream.notification.infrastructure.dispatcher;

import com.paystream.notification.infrastructure.persistence.entity.NotificationEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * SMS dispatcher stub — logs the message that would be sent.
 * Wire Twilio (or similar) in production by replacing this implementation.
 *
 * Production integration:
 *   com.twilio.sdk:twilio → TwilioRestClient → MessageCreator
 *   Credential: TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN (from env vars, never hardcoded)
 */
@Component
public class SmsNotificationDispatcher implements NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(SmsNotificationDispatcher.class);

    @Override
    public String supportedType() {
        return "SMS";
    }

    @Override
    public void dispatch(NotificationEntity notification) {
        // Stub — replace with Twilio integration in production
        log.info("SMS would be sent notificationId={} recipient={} body={}",
                notification.getId(), notification.getRecipient(), notification.getBody());
    }
}
