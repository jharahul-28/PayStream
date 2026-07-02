package com.paystream.notification.infrastructure.dispatcher;

import com.paystream.notification.infrastructure.persistence.entity.NotificationEntity;

/**
 * Strategy interface for notification dispatch.
 * One implementation per channel type: EMAIL, SMS, PUSH.
 */
public interface NotificationDispatcher {

    String supportedType();   // e.g. "EMAIL"

    void dispatch(NotificationEntity notification);
}
