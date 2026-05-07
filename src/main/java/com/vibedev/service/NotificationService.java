package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.dto.notification.NotificationItem;
import com.vibedev.dto.notification.NotificationListResponse;
import com.vibedev.dto.notification.RecentNotificationsResponse;
import com.vibedev.dto.notification.UnreadCountResponse;
import com.vibedev.entity.Notification;
import com.vibedev.repository.NotificationRepository;
import com.vibedev.repository.UserNotificationSettingRepository;
import com.vibedev.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final String UNREAD_COUNT_KEY_PREFIX = "notification:unread:";
    private static final Duration UNREAD_COUNT_TTL = Duration.ofMinutes(5);
    private static final List<String> MANDATORY_EVENTS = List.of("user_banned");

    private final NotificationRepository notificationRepo;
    private final UserNotificationSettingRepository notifySettingRepo;
    private final UserRepository userRepo;
    private final MailService mailService;
    private final StringRedisTemplate redis;

    public NotificationService(NotificationRepository notificationRepo,
                               UserNotificationSettingRepository notifySettingRepo,
                               UserRepository userRepo,
                               MailService mailService,
                               StringRedisTemplate redis) {
        this.notificationRepo = notificationRepo;
        this.notifySettingRepo = notifySettingRepo;
        this.userRepo = userRepo;
        this.mailService = mailService;
        this.redis = redis;
    }

    public void create(String recipientId, String eventType, String title, String body,
                       String sourceType, String sourceId) {
        boolean isMandatory = MANDATORY_EVENTS.contains(eventType);

        // Check site notification preference
        boolean siteEnabled = isMandatory || isChannelEnabled(recipientId, eventType, "site");
        if (siteEnabled) {
            saveNotification(recipientId, eventType, title, body, sourceType, sourceId);
            redis.opsForValue().increment(UNREAD_COUNT_KEY_PREFIX + recipientId);
        }

        // Check email notification preference
        boolean emailEnabled = isMandatory || isChannelEnabled(recipientId, eventType, "email");
        if (emailEnabled) {
            var user = userRepo.findById(recipientId).orElse(null);
            if (user != null && user.getEmail() != null) {
                mailService.sendNotificationEmailForEvent(user.getEmail(), user.getUsername(),
                        eventType, title, body);
            }
        }
    }

    public UnreadCountResponse getUnreadCount(String userId) {
        String key = UNREAD_COUNT_KEY_PREFIX + userId;
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            return new UnreadCountResponse(Long.parseLong(cached));
        }
        long count = notificationRepo.countByRecipientIdAndIsReadFalse(userId);
        redis.opsForValue().set(key, String.valueOf(count), UNREAD_COUNT_TTL);
        return new UnreadCountResponse(count);
    }

    public NotificationListResponse list(String userId, String filter, int page, int limit) {
        if (page < 1) page = 1;
        if (limit < 1 || limit > 50) limit = 20;
        Pageable pageable = PageRequest.of(page - 1, limit);

        var notifPage = switch (filter) {
            case "unread" -> notificationRepo.findByRecipientIdAndIsReadOrderByCreatedAtDesc(userId, false, pageable);
            case "read" -> notificationRepo.findByRecipientIdAndIsReadOrderByCreatedAtDesc(userId, true, pageable);
            default -> notificationRepo.findByRecipientIdOrderByCreatedAtDesc(userId, pageable);
        };

        var items = notifPage.getContent().stream()
                .map(NotificationItem::from)
                .toList();

        long unreadCount = notificationRepo.countByRecipientIdAndIsReadFalse(userId);

        return new NotificationListResponse(items, unreadCount, notifPage.getTotalElements(), page, limit);
    }

    public RecentNotificationsResponse getRecent(String userId) {
        var pageable = PageRequest.of(0, 20,
                Sort.by(Sort.Order.asc("isRead"), Sort.Order.desc("createdAt")));
        var notifPage = notificationRepo.findByRecipientIdOrderByCreatedAtDesc(userId, pageable);

        var items = notifPage.getContent().stream()
                .map(NotificationItem::from)
                .toList();

        long unreadCount = notificationRepo.countByRecipientIdAndIsReadFalse(userId);

        return new RecentNotificationsResponse(items, unreadCount);
    }

    @Transactional
    public void markRead(String userId, String notificationId) {
        var notif = notificationRepo.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "通知不存在"));
        if (!notif.getRecipientId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此通知");
        }
        if (!notif.isRead()) {
            notif.setRead(true);
            notif.setReadAt(java.time.Instant.now());
            notificationRepo.save(notif);
            decrementUnread(userId);
        }
    }

    @Transactional
    public void markAllRead(String userId) {
        int updated = notificationRepo.markAllReadByRecipientId(userId);
        if (updated > 0) {
            redis.opsForValue().set(UNREAD_COUNT_KEY_PREFIX + userId, "0");
        }
    }

    @Transactional
    public void delete(String userId, String notificationId) {
        var notif = notificationRepo.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "通知不存在"));
        if (!notif.getRecipientId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权操作此通知");
        }
        if (!notif.isRead()) {
            decrementUnread(userId);
        }
        notificationRepo.delete(notif);
    }

    // ─── private helpers ───────────────────────────────────

    private void saveNotification(String recipientId, String eventType, String title,
                                  String body, String sourceType, String sourceId) {
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

    private boolean isChannelEnabled(String userId, String eventType, String channel) {
        return notifySettingRepo.findByUserIdAndEventType(userId, eventType)
                .map(setting -> setting.getChannel().equals(channel))
                .orElse(true); // default: enabled if no explicit setting
    }

    private void decrementUnread(String userId) {
        String key = UNREAD_COUNT_KEY_PREFIX + userId;
        String cached = redis.opsForValue().get(key);
        if (cached != null) {
            long val = Long.parseLong(cached);
            if (val > 0) {
                redis.opsForValue().decrement(key);
            }
        }
    }
}
