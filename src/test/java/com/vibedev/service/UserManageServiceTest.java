package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.dto.admin.user.*;
import com.vibedev.entity.User;
import com.vibedev.repository.UserRepository;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserManageServiceTest {

    @Mock private UserRepository userRepo;
    @Mock private MuteService muteService;

    @InjectMocks
    private UserManageService service;

    private User createUser(String id, String username, String role) {
        var u = new User();
        u.setId(id);
        u.setUsername(username);
        u.setNickname(username);
        u.setEmail(username + "@test.com");
        u.setRole(role);
        u.setLevel(1);
        u.setPasswordHash("hash");
        return u;
    }

    @Test
    void listUsers_returnsPaginated() {
        var u1 = createUser("u1", "alice", "user");
        var u2 = createUser("u2", "bob", "moderator");
        Page<User> page = new PageImpl<>(List.of(u1, u2));
        when(userRepo.findUsersForAdmin(isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(page);

        var result = service.listUsers(null, null, null, 1, 20);
        assertEquals(2, result.items().size());
        assertEquals(2, result.total());
    }

    @Test
    void listUsers_withFilters() {
        Page<User> page = new PageImpl<>(List.of());
        when(userRepo.findUsersForAdmin(eq("alice"), eq("user"), isNull(), any(Pageable.class)))
                .thenReturn(page);

        var result = service.listUsers("alice", "user", null, 1, 20);
        assertNotNull(result);
        verify(userRepo).findUsersForAdmin(eq("alice"), eq("user"), isNull(), any(Pageable.class));
    }

    @Test
    void getUserDetail_notFound_throwsError() {
        when(userRepo.findById("notexist")).thenReturn(Optional.empty());

        var ex = assertThrows(BusinessException.class, () -> service.getUserDetail("notexist"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
    }

    @Test
    void getUserDetail_success() {
        var u = createUser("u1", "alice", "user");
        when(userRepo.findById("u1")).thenReturn(Optional.of(u));

        var result = service.getUserDetail("u1");
        assertEquals("alice", result.username());
    }

    @Test
    void updateUser_invalidRole_throwsError() {
        var u = createUser("u1", "alice", "user");
        when(userRepo.findById("u1")).thenReturn(Optional.of(u));

        var dto = new UpdateUserRequest("superadmin", null);
        var ex = assertThrows(BusinessException.class, () -> service.updateUser("u1", dto));
        assertEquals(ErrorCode.DUPLICATE_SUBMIT.getCode(), ex.getCode());
    }

    @Test
    void updateUser_success() {
        var u = createUser("u1", "alice", "user");
        when(userRepo.findById("u1")).thenReturn(Optional.of(u));

        var dto = new UpdateUserRequest("moderator", 3);
        service.updateUser("u1", dto);

        assertEquals("moderator", u.getRole());
        assertEquals(3, u.getLevel());
        verify(userRepo).save(u);
    }

    @Test
    void updateRole_invalidRole_throwsError() {
        when(userRepo.findById("u1")).thenReturn(Optional.of(createUser("u1", "alice", "user")));

        var dto = new UpdateUserRoleRequest("superadmin");
        var ex = assertThrows(BusinessException.class, () -> service.updateRole("u1", dto));
        assertEquals(ErrorCode.DUPLICATE_SUBMIT.getCode(), ex.getCode());
    }

    @Test
    void updateRole_success() {
        var u = createUser("u1", "alice", "user");
        when(userRepo.findById("u1")).thenReturn(Optional.of(u));

        service.updateRole("u1", new UpdateUserRoleRequest("admin"));

        assertEquals("admin", u.getRole());
        verify(userRepo).save(u);
    }

    @Test
    void banUser_delegatesToMuteService() {
        service.banUser("op1", "u1", "bad behavior");
        verify(muteService).muteUser("op1", "admin", "u1", null, "permanent", "bad behavior");
    }

    @Test
    void unbanUser_delegatesToMuteService() {
        service.unbanUser("op1", "u1");
        verify(muteService).unmuteUser("op1", "u1");
    }
}
