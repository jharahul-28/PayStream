package com.paystream.notification.infrastructure.dispatcher;

import com.paystream.notification.infrastructure.persistence.entity.NotificationEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Push notification dispatcher stub.
 * Wire Firebase Cloud Messaging (FCM) or APNs in production.
 */
@Component
public class PushNotificationDispatcher implements NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(PushNotificationDispatcher.class);

    @Override
    public String supportedType() {
        return "PUSH";
    }

    @Override
    public void dispatch(NotificationEntity notification) {
        log.info("PUSH would be sent notificationId={} userId={} body={}",
                notification.getId(), notification.getUserId(), notification.getBody());
    }
}
