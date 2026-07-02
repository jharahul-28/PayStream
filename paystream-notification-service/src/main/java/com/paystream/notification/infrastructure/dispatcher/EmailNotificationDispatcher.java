package com.paystream.notification.infrastructure.dispatcher;

import com.paystream.notification.infrastructure.persistence.entity.NotificationEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Dispatches email notifications via JavaMailSender.
 * In local dev, Mailhog (port 1025) captures emails — visible at http://localhost:8025.
 * In production, wire a real SMTP provider (SendGrid, SES, etc.) via application.yml.
 */
@Component
public class EmailNotificationDispatcher implements NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EmailNotificationDispatcher.class);

    private final JavaMailSender mailSender;

    public EmailNotificationDispatcher(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    @Override
    public String supportedType() {
        return "EMAIL";
    }

    @Override
    public void dispatch(NotificationEntity notification) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(notification.getRecipient());
        message.setSubject(notification.getSubject() != null ? notification.getSubject() : "PayStream Notification");
        message.setText(notification.getBody());
        message.setFrom("noreply@paystream.local");

        mailSender.send(message);
        log.info("Email sent notificationId={} recipient={} channel={}",
                notification.getId(), notification.getRecipient(), notification.getChannel());
    }
}
