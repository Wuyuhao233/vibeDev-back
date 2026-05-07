package com.vibedev.service;

import com.vibedev.entity.Notification;
import com.vibedev.entity.User;
import com.vibedev.entity.UserNotificationSetting;
import com.vibedev.repository.NotificationRepository;
import com.vibedev.repository.UserNotificationSettingRepository;
import com.vibedev.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepo;
    @Mock UserNotificationSettingRepository notifySettingRepo;
    @Mock UserRepository userRepo;
    @Mock MailService mailService;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        // Default: notification settings not found → defaults to enabled (site + email)
        lenient().when(notifySettingRepo.findByUserIdAndEventType(anyString(), anyString()))
                .thenReturn(Optional.empty());

        notificationService = new NotificationService(
                notificationRepo, notifySettingRepo, userRepo, mailService, redis);
    }

    @Test
    void create_shouldSaveNotificationAndIncrRedis() {
        notificationService.create("u1", "post_replied", "帖子被回复",
                "lisi 回复了你的帖子《Test》", "post", "p1");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo).save(captor.capture());

        var notif = captor.getValue();
        assertNotNull(notif.getId());
        assertEquals("u1", notif.getRecipientId());
        assertEquals("post_replied", notif.getEventType());
        assertEquals("帖子被回复", notif.getTitle());
        assertEquals("lisi 回复了你的帖子《Test》", notif.getContent());
        assertEquals("post", notif.getSourceType());
        assertEquals("p1", notif.getSourceId());
        assertFalse(notif.isRead());
        assertNull(notif.getReadAt());

        // Should increment Redis unread count
        verify(valueOps).increment("notification:unread:u1");
    }

    @Test
    void create_likedEvent_shouldSaveCorrectType() {
        notificationService.create("u2", "received_like", "收到点赞",
                "zhangsan 赞了你的回复", "reply", "r1");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo).save(captor.capture());

        var notif = captor.getValue();
        assertEquals("u2", notif.getRecipientId());
        assertEquals("received_like", notif.getEventType());
        assertEquals("reply", notif.getSourceType());
        assertEquals("r1", notif.getSourceId());
        verify(valueOps).increment("notification:unread:u2");
    }

    @Test
    void create_postCollectedEvent_shouldSave() {
        notificationService.create("u3", "post_collected", "帖子被收藏",
                "wangwu 收藏了你的帖子《React 18》", "post", "p2");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo).save(captor.capture());

        assertEquals("post_collected", captor.getValue().getEventType());
    }

    @Test
    void create_postEssencedEvent_shouldSave() {
        notificationService.create("u4", "post_essenced", "帖子被加精",
                "你的帖子《React 18》被版主设为精华", "post", "p3");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo).save(captor.capture());

        assertEquals("post_essenced", captor.getValue().getEventType());
    }

    @Test
    void create_shouldGenerateUniqueIds() {
        notificationService.create("u1", "post_replied", "t", "b", "post", "p1");
        notificationService.create("u1", "post_replied", "t", "b", "post", "p2");

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepo, times(2)).save(captor.capture());

        var all = captor.getAllValues();
        assertNotEquals(all.get(0).getId(), all.get(1).getId());
    }

    @Test
    void create_userBanned_shouldForceSiteAndEmail() {
        var user = new User();
        user.setEmail("banned@test.com");
        user.setUsername("bannedUser");
        when(userRepo.findById("u5")).thenReturn(Optional.of(user));

        notificationService.create("u5", "user_banned", "被禁言",
                "你已被禁言，原因：违规，时长：3天", null, null);

        // Should save notification regardless of preference
        verify(notificationRepo).save(any(Notification.class));
        verify(valueOps).increment("notification:unread:u5");
        // Should send email
        verify(mailService).sendNotificationEmailForEvent(
                eq("banned@test.com"), eq("bannedUser"),
                eq("user_banned"), eq("被禁言"),
                eq("你已被禁言，原因：违规，时长：3天"));
    }

    @Test
    void create_siteDisabled_shouldNotSaveToDb() {
        when(notifySettingRepo.findByUserIdAndEventType("u6", "post_replied"))
                .thenReturn(Optional.empty()); // re-default: enabled
        // Override: disable site for post_replied
        when(notifySettingRepo.findByUserIdAndEventType(anyString(), anyString()))
                .thenReturn(Optional.of(new UserNotificationSetting("s1", "u6", "post_replied", "off")));

        notificationService.create("u6", "post_replied", "帖子被回复",
                "test 回复了你", "post", "p1");

        // off means no site and no email
        verify(notificationRepo, never()).save(any(Notification.class));
        verify(mailService, never()).sendNotificationEmailForEvent(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void create_emailEnabled_shouldSendEmail() {
        var user = new User();
        user.setEmail("user@test.com");
        user.setUsername("testuser");
        when(userRepo.findById("u7")).thenReturn(Optional.of(user));
        when(notifySettingRepo.findByUserIdAndEventType("u7", "post_replied"))
                .thenReturn(Optional.of(new UserNotificationSetting("s2", "u7", "post_replied", "email")));

        notificationService.create("u7", "post_replied", "帖子被回复",
                "test 回复了你", "post", "p1");

        verify(mailService).sendNotificationEmailForEvent(
                eq("user@test.com"), eq("testuser"),
                eq("post_replied"), eq("帖子被回复"),
                eq("test 回复了你"));
    }

    @Test
    void markRead_shouldSetIsReadAndDecrementRedis() {
        var notif = new Notification();
        notif.setId("n1");
        notif.setRecipientId("u1");
        notif.setEventType("post_replied");
        notif.setTitle("t");
        notif.setContent("b");
        notif.setRead(false);
        when(notificationRepo.findById("n1")).thenReturn(Optional.of(notif));
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("notification:unread:u1")).thenReturn("5");

        notificationService.markRead("u1", "n1");

        assertTrue(notif.isRead());
        assertNotNull(notif.getReadAt());
        verify(notificationRepo).save(notif);
        verify(valueOps).decrement("notification:unread:u1");
    }

    @Test
    void markRead_alreadyRead_shouldBeIdempotent() {
        var notif = new Notification();
        notif.setId("n2");
        notif.setRecipientId("u1");
        notif.setEventType("post_replied");
        notif.setTitle("t");
        notif.setContent("b");
        notif.setRead(true);
        when(notificationRepo.findById("n2")).thenReturn(Optional.of(notif));

        notificationService.markRead("u1", "n2");

        // Already read — should not save or decrement
        verify(notificationRepo, never()).save(any());
        verify(valueOps, never()).decrement(anyString());
    }

    @Test
    void markRead_wrongUser_shouldThrow() {
        var notif = new Notification();
        notif.setId("n3");
        notif.setRecipientId("u2");
        notif.setEventType("post_replied");
        notif.setTitle("t");
        notif.setContent("b");
        when(notificationRepo.findById("n3")).thenReturn(Optional.of(notif));

        var ex = assertThrows(com.vibedev.common.BusinessException.class,
                () -> notificationService.markRead("u1", "n3"));
        assertTrue(ex.getMessage().contains("无权"));
    }

    @Test
    void markRead_notFound_shouldThrow() {
        when(notificationRepo.findById("nonexistent")).thenReturn(Optional.empty());

        var ex = assertThrows(com.vibedev.common.BusinessException.class,
                () -> notificationService.markRead("u1", "nonexistent"));
        assertTrue(ex.getMessage().contains("不存在"));
    }

    @Test
    void delete_shouldRemoveAndDecrementUnread() {
        var notif = new Notification();
        notif.setId("n1");
        notif.setRecipientId("u1");
        notif.setEventType("post_replied");
        notif.setTitle("t");
        notif.setContent("b");
        notif.setRead(false);
        when(notificationRepo.findById("n1")).thenReturn(Optional.of(notif));
        when(redis.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("notification:unread:u1")).thenReturn("3");

        notificationService.delete("u1", "n1");

        verify(notificationRepo).delete(notif);
        verify(valueOps).decrement("notification:unread:u1");
    }

    @Test
    void delete_alreadyRead_shouldNotDecrement() {
        var notif = new Notification();
        notif.setId("n2");
        notif.setRecipientId("u1");
        notif.setEventType("post_replied");
        notif.setTitle("t");
        notif.setContent("b");
        notif.setRead(true);
        when(notificationRepo.findById("n2")).thenReturn(Optional.of(notif));

        notificationService.delete("u1", "n2");

        verify(notificationRepo).delete(notif);
        verify(valueOps, never()).decrement(anyString());
    }
}
