package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.dto.admin.moderator.AssignModeratorRequest;
import com.vibedev.dto.admin.moderator.RemoveModeratorRequest;
import com.vibedev.entity.Board;
import com.vibedev.entity.User;
import com.vibedev.repository.BoardRepository;
import com.vibedev.repository.ModeratorBoardRepository;
import com.vibedev.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModeratorServiceTest {

    @Mock ModeratorBoardRepository moderatorBoardRepo;
    @Mock UserRepository userRepo;
    @Mock BoardRepository boardRepo;

    @InjectMocks ModeratorService moderatorService;

    private User createUser(String id, String role) {
        var u = new User();
        u.setId(id);
        u.setUsername("user_" + id);
        u.setNickname("Nick_" + id);
        u.setAvatarUrl("/avatar.png");
        u.setRole(role);
        u.setLevel(2);
        u.setPoints(100);
        return u;
    }

    @Test
    void assignModerator_shouldAssignBoardsAndUpdateRole() {
        var user = createUser("u1", "user");
        var request = new AssignModeratorRequest("u1", List.of("b1", "b2"));

        when(userRepo.findById("u1")).thenReturn(Optional.of(user));
        when(boardRepo.existsById("b1")).thenReturn(true);
        when(boardRepo.existsById("b2")).thenReturn(true);

        moderatorService.assignModerator(request);

        assertEquals("moderator", user.getRole());
        verify(moderatorBoardRepo).deleteByUserId("u1");
        verify(moderatorBoardRepo, times(2)).save(any());
        verify(userRepo).save(user);
    }

    @Test
    void assignModerator_shouldThrowWhenUserNotFound() {
        var request = new AssignModeratorRequest("u99", List.of("b1"));
        when(userRepo.findById("u99")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class,
                () -> moderatorService.assignModerator(request));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void assignModerator_shouldThrowWhenBoardNotFound() {
        var user = createUser("u1", "user");
        var request = new AssignModeratorRequest("u1", List.of("b1", "b99"));

        when(userRepo.findById("u1")).thenReturn(Optional.of(user));
        when(boardRepo.existsById("b1")).thenReturn(true);
        when(boardRepo.existsById("b99")).thenReturn(false);

        var ex = assertThrows(BusinessException.class,
                () -> moderatorService.assignModerator(request));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        verify(moderatorBoardRepo, never()).deleteByUserId(any());
    }

    @Test
    void assignModerator_shouldThrowWhenTargetIsAdmin() {
        var adminUser = createUser("admin1", "admin");
        var request = new AssignModeratorRequest("admin1", List.of("b1"));

        when(userRepo.findById("admin1")).thenReturn(Optional.of(adminUser));

        var ex = assertThrows(BusinessException.class,
                () -> moderatorService.assignModerator(request));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void assignModerator_shouldDemoteToUserWhenBoardIdsEmpty() {
        var user = createUser("u1", "moderator");
        var request = new AssignModeratorRequest("u1", List.of());

        when(userRepo.findById("u1")).thenReturn(Optional.of(user));

        moderatorService.assignModerator(request);

        assertEquals("user", user.getRole());
        verify(moderatorBoardRepo).deleteByUserId("u1");
        verify(userRepo).save(user);
    }

    @Test
    void removeModerator_shouldClearBoardsAndResetRole() {
        var user = createUser("u1", "moderator");
        var request = new RemoveModeratorRequest("u1", "撤职原因");

        when(userRepo.findById("u1")).thenReturn(Optional.of(user));

        moderatorService.removeModerator(request);

        assertEquals("user", user.getRole());
        verify(moderatorBoardRepo).deleteByUserId("u1");
        verify(userRepo).save(user);
    }

    @Test
    void removeModerator_shouldThrowWhenTargetIsAdmin() {
        var adminUser = createUser("admin1", "admin");
        var request = new RemoveModeratorRequest("admin1", "不能撤销管理员");

        when(userRepo.findById("admin1")).thenReturn(Optional.of(adminUser));

        var ex = assertThrows(BusinessException.class,
                () -> moderatorService.removeModerator(request));
        assertEquals(ErrorCode.FORBIDDEN.getCode(), ex.getCode());
    }

    @Test
    void removeModerator_shouldThrowWhenUserNotFound() {
        var request = new RemoveModeratorRequest("u99", "");
        when(userRepo.findById("u99")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class,
                () -> moderatorService.removeModerator(request));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void getModeratorBoardIds_shouldReturnBoardIds() {
        var mb = mock(com.vibedev.entity.ModeratorBoard.class);
        when(mb.getBoardId()).thenReturn("b1");
        when(moderatorBoardRepo.findByUserId("u1")).thenReturn(List.of(mb));

        var ids = moderatorService.getModeratorBoardIds("u1");
        assertEquals(List.of("b1"), ids);
    }
}
