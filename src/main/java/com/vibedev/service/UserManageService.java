package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.dto.admin.user.*;
import com.vibedev.entity.User;
import com.vibedev.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserManageService {

    private static final Logger log = LoggerFactory.getLogger(UserManageService.class);

    private final UserRepository userRepo;
    private final MuteService muteService;

    public UserManageService(UserRepository userRepo, MuteService muteService) {
        this.userRepo = userRepo;
        this.muteService = muteService;
    }

    public com.vibedev.common.PaginatedResponse<AdminUserItem> listUsers(
            String search, String role, String status, int page, int limit) {
        if (page < 1) page = 1;
        if (limit < 1 || limit > 100) limit = 20;

        var pageable = PageRequest.of(page - 1, limit);
        var result = userRepo.findUsersForAdmin(
                search != null && !search.isBlank() ? search : null,
                role != null && !role.isBlank() ? role : null,
                status != null && !status.isBlank() ? status : null,
                pageable);

        var items = result.getContent().stream()
                .map(u -> AdminUserItem.from(u, 0, 0))
                .toList();

        return com.vibedev.common.PaginatedResponse.of(items, result.getTotalElements(), page, limit);
    }

    public AdminUserDetailResponse getUserDetail(String userId) {
        var user = userRepo.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        return AdminUserDetailResponse.from(user);
    }

    @Transactional
    public void updateUser(String userId, UpdateUserRequest dto) {
        var user = userRepo.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        if (dto.role() != null) {
            if (!List.of("user", "moderator", "admin").contains(dto.role())) {
                throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "无效的角色: " + dto.role());
            }
            user.setRole(dto.role());
        }
        if (dto.level() != null) {
            if (dto.level() < 1 || dto.level() > 10) {
                throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "等级范围1-10");
            }
            user.setLevel(dto.level());
        }

        userRepo.save(user);
    }

    @Transactional
    public void updateRole(String userId, UpdateUserRoleRequest dto) {
        if (!List.of("user", "moderator", "admin").contains(dto.role())) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "无效的角色: " + dto.role());
        }
        var user = userRepo.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        user.setRole(dto.role());
        userRepo.save(user);
    }

    @Transactional
    public void banUser(String operatorId, String targetUserId, String reason) {
        muteService.muteUser(operatorId, "admin", targetUserId, null, "permanent", reason);
    }

    @Transactional
    public void unbanUser(String operatorId, String targetUserId) {
        muteService.unmuteUser(operatorId, targetUserId);
    }
}
