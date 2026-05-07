package com.vibedev.service;

import com.vibedev.entity.Notification;
import com.vibedev.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock NotificationRepository notificationRepo;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepo);
    }

    @Test
    void create_shouldSaveNotification() {
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
        assertNotNull(notif.getCreatedAt());
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
}
