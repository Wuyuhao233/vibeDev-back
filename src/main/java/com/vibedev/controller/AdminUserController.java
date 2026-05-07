package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.common.PaginatedResponse;
import com.vibedev.dto.admin.user.*;
import com.vibedev.service.UserManageService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminUserController {

    private final UserManageService userManageService;

    public AdminUserController(UserManageService userManageService) {
        this.userManageService = userManageService;
    }

    @GetMapping("/users")
    public ApiResponse<PaginatedResponse<AdminUserItem>> listUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(userManageService.listUsers(search, role, status, page, limit));
    }

    @GetMapping("/users/{id}")
    public ApiResponse<AdminUserDetailResponse> getUserDetail(@PathVariable String id) {
        return ApiResponse.ok(userManageService.getUserDetail(id));
    }

    @PutMapping("/users/{id}")
    public ApiResponse<Void> updateUser(@PathVariable String id,
                                         @Valid @RequestBody UpdateUserRequest dto) {
        userManageService.updateUser(id, dto);
        return ApiResponse.ok();
    }

    @PutMapping("/users/{id}/role")
    public ApiResponse<Void> updateRole(@PathVariable String id,
                                         @Valid @RequestBody UpdateUserRoleRequest dto) {
        userManageService.updateRole(id, dto);
        return ApiResponse.ok();
    }
}
