package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.entity.MuteRecord;
import com.vibedev.entity.User;
import com.vibedev.repository.MuteRecordRepository;
import com.vibedev.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MuteServiceTest {

    @Mock MuteRecordRepository muteRecordRepo;
    @Mock UserRepository userRepo;
    @Mock NotificationService notificationService;
    @Mock PointsService pointsService;

    private MuteService muteService;

    @BeforeEach
    void setUp() {
        muteService = new MuteService(muteRecordRepo, userRepo, notificationService, pointsService);
    }

    private User createUser(String id, String role) {
        var u = new User();
        u.setId(id);
        u.setUsername("user_" + id);
        u.setNickname("Nick" + id);
        u.setRole(role);
        u.setBanned(false);
        return u;
    }

    @Test
    void muteUserShouldSucceed() {
        var target = createUser("u2", "user");
        when(userRepo.findById("u2")).thenReturn(Optional.of(target));

        muteService.muteUser("u1", "admin", "u2", null, "1d", "违规测试");

        assertTrue(target.isBanned());
        assertNotNull(target.getBannedUntil());
        verify(muteRecordRepo).save(any(MuteRecord.class));
        verify(notificationService).create(eq("u2"), eq("user_banned"), anyString(), anyString(), eq(null), eq(null));
    }

    @Test
    void muteAdminShouldFail() {
        var admin = createUser("admin1", "admin");
        when(userRepo.findById("admin1")).thenReturn(Optional.of(admin));

        assertThrows(BusinessException.class, () ->
                muteService.muteUser("u1", "admin", "admin1", null, "1d", "test"));
    }

    @Test
    void unmuteUserShouldSucceed() {
        var target = createUser("u2", "user");
        target.setBanned(true);
        target.setBannedUntil(Instant.now().plusSeconds(3600));
        when(userRepo.findById("u2")).thenReturn(Optional.of(target));
        when(muteRecordRepo.findByUserIdAndIsActiveTrue("u2")).thenReturn(Optional.empty());

        muteService.unmuteUser("admin1", "u2");

        assertFalse(target.isBanned());
        assertNull(target.getBannedUntil());
    }

    @Test
    void unmuteNotBannedUserShouldFail() {
        var target = createUser("u2", "user");
        when(userRepo.findById("u2")).thenReturn(Optional.of(target));

        assertThrows(BusinessException.class, () ->
                muteService.unmuteUser("admin1", "u2"));
    }

    @Test
    void isUserBannedShouldReturnTrue() {
        var target = createUser("u2", "user");
        target.setBanned(true);
        target.setBannedUntil(Instant.now().plusSeconds(3600));
        when(userRepo.findById("u2")).thenReturn(Optional.of(target));

        assertTrue(muteService.isUserBanned("u2"));
    }

    @Test
    void isUserBannedWithExpiredShouldReturnFalse() {
        var target = createUser("u2", "user");
        target.setBanned(true);
        target.setBannedUntil(Instant.now().minusSeconds(3600));
        when(userRepo.findById("u2")).thenReturn(Optional.of(target));

        assertFalse(muteService.isUserBanned("u2"));
        assertFalse(target.isBanned());
    }

    @Test
    void getHistoryShouldReturnRecords() {
        var record = new MuteRecord();
        record.setId("mr1");
        record.setUserId("u2");
        record.setModeratorId("u1");
        record.setDuration("1d");
        record.setReason("test");
        when(muteRecordRepo.findByUserIdOrderByCreatedAtDesc("u2")).thenReturn(List.of(record));

        var history = muteService.getHistory("u2");
        assertEquals(1, history.size());
        assertEquals("mr1", history.get(0).id());
    }
}
