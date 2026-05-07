package com.vibedev.service;

import com.vibedev.entity.Notification;
import com.vibedev.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepo;

    public NotificationService(NotificationRepository notificationRepo) {
        this.notificationRepo = notificationRepo;
    }

    public void create(String recipientId, String eventType, String title, String body,
                       String sourceType, String sourceId) {
        var notification = new Notification();
        notification.setId(UUID.randomUUID().toString());
        notification.setRecipientId(recipientId);
        notification.setEventType(eventType);
        notification.setTitle(title);
        notification.setContent(body);
        notification.setSourceType(sourceType);
        notification.setSourceId(sourceId);
        notificationRepo.save(notification);
        log.debug("Notification created: recipient={}, type={}", recipientId, eventType);
    }
}
