package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.common.PaginatedResponse;
import com.vibedev.dto.user.*;
import com.vibedev.entity.*;
import com.vibedev.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserServiceTest {

    @Mock UserRepository userRepo;
    @Mock PostRepository postRepo;
    @Mock ReplyRepository replyRepo;
    @Mock FavoriteRepository favoriteRepo;
    @Mock BoardRepository boardRepo;
    @Mock BrowsingHistoryRepository browsingHistoryRepo;
    @Mock LoginHistoryRepository loginHistoryRepo;
    @Mock UserExportTaskRepository exportTaskRepo;
    @Mock UserNotificationSettingRepository notifySettingRepo;
    @Mock FileStorageService fileStorageService;
    @Mock PasswordEncoder passwordEncoder;
    @Mock CasService casService;
    @Mock FollowService followService;
    @Mock StringRedisTemplate redis;
    @Mock ValueOperations<String, String> valueOps;

    @InjectMocks UserService userService;

    private final String baseUrl = "http://localhost:8080";

    @BeforeEach
    void setUp() {
        userService = new UserService(userRepo, postRepo, replyRepo, favoriteRepo,
                boardRepo, browsingHistoryRepo, loginHistoryRepo, exportTaskRepo,
                notifySettingRepo, fileStorageService, passwordEncoder, casService, followService, redis, baseUrl);
        when(redis.opsForValue()).thenReturn(valueOps);
    }

    private User createUser(String id, String username) {
        var user = new User();
        user.setId(id);
        user.setUsername(username);
        user.setEmail(username + "@test.com");
        user.setNickname(username);
        user.setSignature("Hello");
        user.setAvatarUrl("/avatar.png");
        user.setRole("user");
        user.setLevel(1);
        user.setPoints(100);
        user.setActivated(true);
        user.setPasswordHash("hashed");
        user.setTokenVersion(0);
        return user;
    }

    // ─── Profile ───────────────────────────────────────────

    @Test
    void getProfile_shouldReturnMaskedEmail() {
        var user = createUser("u1", "alice");
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        when(followService.getFollowerCount("u1")).thenReturn(5L);
        when(followService.getFollowingCount("u1")).thenReturn(3L);

        var profile = userService.getProfile("alice", null);

        assertEquals("alice", profile.username());
        assertTrue(profile.email().contains("***"));
        assertEquals("Hello", profile.signature());
        assertEquals(5L, profile.followerCount());
        assertEquals(3L, profile.followingCount());
    }

    @Test
    void getProfile_shouldThrowWhenUserNotFound() {
        when(userRepo.findByUsername("nobody")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class, () -> userService.getProfile("nobody", null));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ─── User Posts ────────────────────────────────────────

    @Test
    void getUserPosts_shouldReturnPagedPosts() {
        var user = createUser("u1", "alice");
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));

        var post = mock(Post.class);
        when(post.getId()).thenReturn("p1");
        when(post.getTitle()).thenReturn("Test Post");
        when(post.getBoardId()).thenReturn("b1");
        when(post.getLikeCount()).thenReturn(5);
        when(post.getReplyCount()).thenReturn(3);
        when(post.getCollectCount()).thenReturn(2);
        when(post.isEssence()).thenReturn(false);
        when(post.isDeleted()).thenReturn(false);
        when(post.getCreatedAt()).thenReturn(Instant.now());

        Page<Post> page = new PageImpl<>(List.of(post));
        when(postRepo.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(eq("u1"), any()))
                .thenReturn(page);

        var board = mock(Board.class);
        when(board.getName()).thenReturn("General");
        when(boardRepo.findById("b1")).thenReturn(Optional.of(board));

        var result = userService.getUserPosts("alice", 1, 20);

        assertNotNull(result);
        assertEquals(1, result.items().size());
        assertEquals("Test Post", result.items().get(0).title());
    }

    // ─── User Replies ──────────────────────────────────────

    @Test
    void getUserReplies_shouldReturnPagedReplies() {
        var user = createUser("u1", "alice");
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));

        var reply = mock(Reply.class);
        when(reply.getId()).thenReturn("r1");
        when(reply.getPostId()).thenReturn("p1");
        when(reply.getContentMarkdown()).thenReturn("Great post!");
        when(reply.getLikeCount()).thenReturn(10);
        when(reply.isDeleted()).thenReturn(false);
        when(reply.getCreatedAt()).thenReturn(Instant.now());

        Page<Reply> page = new PageImpl<>(List.of(reply));
        when(replyRepo.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(eq("u1"), any()))
                .thenReturn(page);

        var post = mock(Post.class);
        when(post.getTitle()).thenReturn("Original Post");
        when(postRepo.findById("p1")).thenReturn(Optional.of(post));

        var result = userService.getUserReplies("alice", 1, 20);

        assertEquals(1, result.items().size());
        assertEquals("Original Post", result.items().get(0).postTitle());
        assertEquals("Great post!", result.items().get(0).contentSummary());
    }

    // ─── Favorites ─────────────────────────────────────────

    @Test
    void getUserFavorites_shouldReturnOwnFavorites() {
        var user = createUser("u1", "alice");
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));

        var fav = mock(Favorite.class);
        when(fav.getId()).thenReturn("f1");
        when(fav.getPostId()).thenReturn("p1");
        when(fav.getCreatedAt()).thenReturn(Instant.now());

        Page<Favorite> page = new PageImpl<>(List.of(fav));
        when(favoriteRepo.findByUserIdOrderByCreatedAtDesc(eq("u1"), any()))
                .thenReturn(page);

        var post = mock(Post.class);
        when(post.getTitle()).thenReturn("Fav Post");
        when(post.getAuthorId()).thenReturn("u2");
        when(post.getBoardId()).thenReturn("b1");
        when(postRepo.findById("p1")).thenReturn(Optional.of(post));

        var author = createUser("u2", "bob");
        when(userRepo.findById("u2")).thenReturn(Optional.of(author));
        var board = mock(Board.class);
        when(board.getName()).thenReturn("General");
        when(boardRepo.findById("b1")).thenReturn(Optional.of(board));

        var result = userService.getUserFavorites("alice", "u1", 1, 20);

        assertEquals(1, result.items().size());
        assertEquals("Fav Post", result.items().get(0).postTitle());
    }

    @Test
    void getUserFavorites_shouldRejectOtherUser() {
        var user = createUser("u1", "alice");
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));

        var ex = assertThrows(BusinessException.class,
                () -> userService.getUserFavorites("alice", "u2", 1, 20));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    // ─── History ───────────────────────────────────────────

    @Test
    void getUserHistory_shouldReturnBrowsingHistory() {
        var user = createUser("u1", "alice");
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));

        var bh = mock(BrowsingHistory.class);
        when(bh.getPostId()).thenReturn("p1");
        when(bh.getViewedAt()).thenReturn(Instant.now());

        Page<BrowsingHistory> page = new PageImpl<>(List.of(bh));
        when(browsingHistoryRepo.findByUserIdOrderByViewedAtDesc(eq("u1"), any()))
                .thenReturn(page);

        var post = mock(Post.class);
        when(post.getTitle()).thenReturn("Viewed Post");
        when(postRepo.findById("p1")).thenReturn(Optional.of(post));

        var result = userService.getUserHistory("alice", "u1", 1, 20);

        assertEquals(1, result.items().size());
        assertEquals("Viewed Post", result.items().get(0).postTitle());
    }

    @Test
    void getUserHistory_shouldRejectOtherUser() {
        var user = createUser("u1", "alice");
        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));

        var ex = assertThrows(BusinessException.class,
                () -> userService.getUserHistory("alice", "u2", 1, 20));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    // ─── Update Profile ────────────────────────────────────

    @Test
    void updateProfile_shouldUpdateFields() {
        var user = createUser("u1", "alice");
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));

        var req = new UpdateProfileRequest("NewNick", "New sig", "/new-avatar.png");
        var result = userService.updateProfile("u1", req);

        assertEquals("NewNick", user.getNickname());
        assertEquals("New sig", user.getSignature());
        assertEquals("/new-avatar.png", user.getAvatarUrl());
        assertEquals("NewNick", result.nickname());
    }

    @Test
    void updateProfile_shouldRejectInvalidNickname() {
        var user = createUser("u1", "alice");
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));

        var req = new UpdateProfileRequest("A", null, null);
        var ex = assertThrows(BusinessException.class, () -> userService.updateProfile("u1", req));
        assertEquals(ErrorCode.UNKNOWN.getCode(), ex.getCode());
    }

    // ─── Change Password ───────────────────────────────────

    @Test
    void changePassword_shouldUpdateAndBumpVersion() {
        var user = createUser("u1", "alice");
        int oldVersion = user.getTokenVersion();
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("old", "hashed")).thenReturn(true);
        when(passwordEncoder.encode("NewPass1")).thenReturn("new-hashed");

        var req = new ChangePasswordRequest("old", "NewPass1");
        var result = userService.changePassword("u1", req);

        assertEquals("new-hashed", user.getPasswordHash());
        assertEquals(oldVersion + 1, user.getTokenVersion());
        assertTrue(result.message().contains("已修改"));
    }

    @Test
    void changePassword_shouldThrowOnWrongOldPassword() {
        var user = createUser("u1", "alice");
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        var req = new ChangePasswordRequest("wrong", "NewPass1");
        var ex = assertThrows(BusinessException.class, () -> userService.changePassword("u1", req));
        assertEquals(ErrorCode.PASSWORD_WRONG.getCode(), ex.getCode());
    }

    // ─── Deactivate / Recover ──────────────────────────────

    @Test
    void deactivateAccount_shouldMarkDeactivated() {
        var user = createUser("u1", "alice");
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Pass1234", "hashed")).thenReturn(true);

        var result = userService.deactivateAccount("alice", "u1", "Pass1234");

        assertTrue(user.isDeactivated());
        assertNotNull(user.getDeactivatedAt());
        assertNotNull(user.getRecoveryDeadline());
        assertNotNull(result);
    }

    @Test
    void deactivateAccount_shouldThrowOnWrongPassword() {
        var user = createUser("u1", "alice");
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        var ex = assertThrows(BusinessException.class,
                () -> userService.deactivateAccount("alice", "u1", "wrong"));
        assertEquals(ErrorCode.PASSWORD_WRONG.getCode(), ex.getCode());
    }

    @Test
    void recoverAccount_shouldRestoreAccount() {
        var user = createUser("u1", "alice");
        user.setDeactivated(true);
        user.setDeactivatedAt(Instant.now().minusSeconds(86400));
        user.setRecoveryDeadline(Instant.now().plusSeconds(86400 * 29));

        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Pass1234", "hashed")).thenReturn(true);

        assertDoesNotThrow(() ->
                userService.recoverAccount("alice", new RecoverAccountRequest("a@b.com", "Pass1234")));

        assertFalse(user.isDeactivated());
        assertNull(user.getDeactivatedAt());
        assertNull(user.getRecoveryDeadline());
    }

    @Test
    void recoverAccount_shouldThrowWhenExpired() {
        var user = createUser("u1", "alice");
        user.setDeactivated(true);
        user.setRecoveryDeadline(Instant.now().minusSeconds(3600));

        when(userRepo.findByUsername("alice")).thenReturn(Optional.of(user));

        var ex = assertThrows(BusinessException.class,
                () -> userService.recoverAccount("alice", new RecoverAccountRequest("a@b.com", "Pass1234")));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    // ─── Data Export ───────────────────────────────────────

    @Test
    void exportData_shouldCreateTask() {
        var user = createUser("u1", "alice");
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));
        when(valueOps.increment("export:limit:u1:24h")).thenReturn(1L);

        var result = userService.exportData("alice", "u1", new ExportDataRequest("all"));

        assertNotNull(result.taskId());
        assertEquals("processing", result.status());
        verify(exportTaskRepo).save(any(UserExportTask.class));
    }

    @Test
    void exportData_shouldRateLimit() {
        var user = createUser("u1", "alice");
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));
        when(valueOps.increment("export:limit:u1:24h")).thenReturn(2L);

        var ex = assertThrows(BusinessException.class,
                () -> userService.exportData("alice", "u1", new ExportDataRequest("all")));
        assertEquals(ErrorCode.RATE_LIMITED.getCode(), ex.getCode());
    }

    // ─── Login History ─────────────────────────────────────

    @Test
    void getLoginHistory_shouldReturnPaginated() {
        var lh = new LoginHistory(UUID.randomUUID().toString(), "u1", "email",
                "127.0.0.1", "Chrome", true);
        Page<LoginHistory> page = new PageImpl<>(List.of(lh));
        when(loginHistoryRepo.findByUserIdOrderByCreatedAtDesc(eq("u1"), any()))
                .thenReturn(page);

        var result = userService.getLoginHistory("u1", 1, 20);

        assertEquals(1, result.items().size());
        assertEquals("email", result.items().get(0).loginType());
        assertTrue(result.items().get(0).success());
    }

    // ─── Browsing History Recording ────────────────────────

    @Test
    void recordBrowsing_shouldCreateNewRecord() {
        when(browsingHistoryRepo.findByUserIdAndPostId("u1", "p1"))
                .thenReturn(Optional.empty());

        userService.recordBrowsing("u1", "p1");

        verify(browsingHistoryRepo).save(any(BrowsingHistory.class));
    }

    @Test
    void recordBrowsing_shouldUpdateExistingWithin1h() {
        var bh = new BrowsingHistory("bh1", "u1", "p1");
        Instant now = Instant.now();
        bh.setViewedAt(now.minusSeconds(1800)); // 30 min ago

        when(browsingHistoryRepo.findByUserIdAndPostId("u1", "p1"))
                .thenReturn(Optional.of(bh));

        userService.recordBrowsing("u1", "p1");

        // viewedAt should be updated (within 1h)
        assertTrue(bh.getViewedAt().isAfter(now.minusSeconds(10)));
        verify(browsingHistoryRepo).save(bh);
    }

    // ─── Notification Settings ──────────────────────────────

    @Test
    void getNotifySettings_shouldReturnDefaultsWhenNoSettingsExist() {
        when(notifySettingRepo.findByUserId("u1")).thenReturn(List.of());

        var result = userService.getNotifySettings("u1");

        assertNotNull(result);
        assertEquals(7, result.preferences().size());
        assertTrue(result.preferences().stream().allMatch(p -> "site".equals(p.channel())));
        assertEquals(1, result.mandatoryEvents().size());
        assertTrue(result.mandatoryEvents().contains("user_banned"));
    }

    @Test
    void getNotifySettings_shouldReturnPersistedSettings() {
        var setting = new UserNotificationSetting("s1", "u1", "post_replied", "email");
        when(notifySettingRepo.findByUserId("u1")).thenReturn(List.of(setting));

        var result = userService.getNotifySettings("u1");

        assertEquals(1, result.preferences().size());
        assertEquals("post_replied", result.preferences().get(0).eventType());
        assertEquals("email", result.preferences().get(0).channel());
    }

    @Test
    void updateNotifySettings_shouldUpsertPreferences() {
        var existing = new UserNotificationSetting("s1", "u1", "post_replied", "site");
        when(notifySettingRepo.findByUserIdAndEventType("u1", "post_replied"))
                .thenReturn(Optional.of(existing));
        when(notifySettingRepo.findByUserIdAndEventType("u1", "received_like"))
                .thenReturn(Optional.empty());

        var req = new UpdateNotificationSettingsRequest(List.of(
                new NotificationPreference("post_replied", "email"),
                new NotificationPreference("received_like", "off")
        ));

        assertDoesNotThrow(() -> userService.updateNotifySettings("u1", req));

        assertEquals("email", existing.getChannel());
        verify(notifySettingRepo).save(existing);
        verify(notifySettingRepo, times(2)).save(any(UserNotificationSetting.class));
    }

    @Test
    void updateNotifySettings_shouldRejectInvalidEventType() {
        var req = new UpdateNotificationSettingsRequest(List.of(
                new NotificationPreference("invalid_event", "site")
        ));

        var ex = assertThrows(BusinessException.class,
                () -> userService.updateNotifySettings("u1", req));
        assertEquals(ErrorCode.UNKNOWN.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("无效的事件类型"));
    }

    @Test
    void updateNotifySettings_shouldRejectInvalidChannel() {
        var req = new UpdateNotificationSettingsRequest(List.of(
                new NotificationPreference("post_replied", "sms")
        ));

        var ex = assertThrows(BusinessException.class,
                () -> userService.updateNotifySettings("u1", req));
        assertEquals(ErrorCode.UNKNOWN.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("无效的通知渠道"));
    }

    @Test
    void updateNotifySettings_shouldIgnoreMandatoryEventOff() {
        when(notifySettingRepo.findByUserIdAndEventType("u1", "user_banned"))
                .thenReturn(Optional.empty());

        var req = new UpdateNotificationSettingsRequest(List.of(
                new NotificationPreference("user_banned", "off")
        ));

        assertDoesNotThrow(() -> userService.updateNotifySettings("u1", req));

        verify(notifySettingRepo, never()).save(any(UserNotificationSetting.class));
    }

    @Test
    void updateNotifySettings_shouldAllowMandatoryEventSite() {
        var existing = new UserNotificationSetting("s1", "u1", "user_banned", "site");
        when(notifySettingRepo.findByUserIdAndEventType("u1", "user_banned"))
                .thenReturn(Optional.of(existing));

        var req = new UpdateNotificationSettingsRequest(List.of(
                new NotificationPreference("user_banned", "email")
        ));

        assertDoesNotThrow(() -> userService.updateNotifySettings("u1", req));

        assertEquals("email", existing.getChannel());
        verify(notifySettingRepo).save(existing);
    }

    @Test
    void updateNotifySettings_shouldIgnoreEmptyPreferences() {
        var req = new UpdateNotificationSettingsRequest(List.of());

        assertDoesNotThrow(() -> userService.updateNotifySettings("u1", req));

        verify(notifySettingRepo, never()).findByUserIdAndEventType(any(), any());
        verify(notifySettingRepo, never()).save(any());
    }

    @Test
    void updateNotifySettings_shouldIgnoreNullPreferences() {
        var req = new UpdateNotificationSettingsRequest(null);

        assertDoesNotThrow(() -> userService.updateNotifySettings("u1", req));

        verify(notifySettingRepo, never()).findByUserIdAndEventType(any(), any());
        verify(notifySettingRepo, never()).save(any());
    }

    // ─── CAS Binding ────────────────────────────────────

    @Test
    void bindCas_shouldBindCasIdToUser() {
        var user = createUser("u1", "alice");
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));

        var attrs = new CasService.CasUserAttributes("cas-123", "casuser", "cas@test.com");
        when(casService.validateCode("OC-valid", "http://redirect")).thenReturn(attrs);
        when(userRepo.findByCasId("cas-123")).thenReturn(Optional.empty());

        var result = userService.bindCas("u1", new BindCasRequest("OC-valid", "http://redirect"));

        assertTrue(result.isBound());
        assertEquals("casuser", result.casUsername());
        assertEquals("cas-123", user.getCasId());
        verify(userRepo).save(user);
    }

    @Test
    void bindCas_shouldThrowWhenAlreadyBound() {
        var user = createUser("u1", "alice");
        user.setCasId("existing-cas");
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));

        var ex = assertThrows(BusinessException.class,
                () -> userService.bindCas("u1", new BindCasRequest("OC-xxx", "http://redirect")));
        assertEquals(ErrorCode.UNKNOWN.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("已绑定"));
    }

    @Test
    void bindCas_shouldThrowWhenCasIdTakenByAnotherUser() {
        var user = createUser("u1", "alice");
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));

        var otherUser = createUser("u2", "bob");
        otherUser.setCasId("cas-123");

        var attrs = new CasService.CasUserAttributes("cas-123", "casuser", "cas@test.com");
        when(casService.validateCode("OC-taken", "http://redirect")).thenReturn(attrs);
        when(userRepo.findByCasId("cas-123")).thenReturn(Optional.of(otherUser));

        var ex = assertThrows(BusinessException.class,
                () -> userService.bindCas("u1", new BindCasRequest("OC-taken", "http://redirect")));
        assertEquals(ErrorCode.EMAIL_TAKEN.getCode(), ex.getCode());
    }

    @Test
    void unbindCas_shouldClearCasId() {
        var user = createUser("u1", "alice");
        user.setCasId("cas-123");
        user.setPasswordHash("hashed");
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));

        var result = userService.unbindCas("u1");

        assertFalse(result.isBound());
        assertNull(user.getCasId());
        verify(userRepo).save(user);
    }

    @Test
    void unbindCas_shouldThrowWhenNoPassword() {
        var user = createUser("u1", "alice");
        user.setCasId("cas-123");
        user.setPasswordHash(""); // CAS-only user, no password
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));

        var ex = assertThrows(BusinessException.class,
                () -> userService.unbindCas("u1"));
        assertEquals(ErrorCode.UNKNOWN.getCode(), ex.getCode());
        assertTrue(ex.getMessage().contains("密码"));
    }

    @Test
    void unbindCas_shouldReturnNotBoundWhenNoCasId() {
        var user = createUser("u1", "alice");
        when(userRepo.findById("u1")).thenReturn(Optional.of(user));

        var result = userService.unbindCas("u1");

        assertFalse(result.isBound());
        verify(userRepo, never()).save(any(User.class));
    }
}
