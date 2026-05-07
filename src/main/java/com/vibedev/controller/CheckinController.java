package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.repository.UserRepository;
import com.vibedev.security.SecurityHelper;
import com.vibedev.service.CheckinService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class CheckinController {

    private final CheckinService checkinService;
    private final UserRepository userRepo;

    public CheckinController(CheckinService checkinService, UserRepository userRepo) {
        this.checkinService = checkinService;
        this.userRepo = userRepo;
    }

    @PostMapping("/users/{username}/sign-in")
    public ApiResponse<?> signIn(@PathVariable String username, Authentication auth) {
        String currentUserId = SecurityHelper.getUserId(auth);
        var user = userRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        if (!user.getId().equals(currentUserId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅可本人签到");
        }
        try {
            var result = checkinService.checkin(currentUserId);
            return ApiResponse.ok(result);
        } catch (BusinessException e) {
            if (e.getCode() == ErrorCode.DUPLICATE_SUBMIT.getCode()) {
                var status = checkinService.getCheckinStatus(currentUserId);
                return new ApiResponse<>(e.getCode(), "今日已签到",
                        Map.of("consecutive_days", status.consecutiveDays()));
            }
            throw e;
        }
    }

    @GetMapping("/users/{username}/sign-in/status")
    public ApiResponse<?> getStatus(@PathVariable String username, Authentication auth) {
        SecurityHelper.getUserId(auth);
        var user = userRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        var result = checkinService.getCheckinStatus(user.getId());
        return ApiResponse.ok(result);
    }
}
