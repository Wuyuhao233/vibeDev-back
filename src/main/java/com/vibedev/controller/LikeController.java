package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.dto.like.LikeStatusResponse;
import com.vibedev.dto.like.LikeToggleResponse;
import com.vibedev.security.SecurityHelper;
import com.vibedev.service.LikeService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    @PostMapping("/posts/{id}/like")
    public ApiResponse<LikeToggleResponse> likePost(@PathVariable String id, Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        return ApiResponse.ok(likeService.like(userId, "post", id));
    }

    @DeleteMapping("/posts/{id}/like")
    public ApiResponse<LikeToggleResponse> unlikePost(@PathVariable String id, Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        return ApiResponse.ok(likeService.unlike(userId, "post", id));
    }

    @PostMapping("/replies/{id}/like")
    public ApiResponse<LikeToggleResponse> likeReply(@PathVariable String id, Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        return ApiResponse.ok(likeService.like(userId, "reply", id));
    }

    @DeleteMapping("/replies/{id}/like")
    public ApiResponse<LikeToggleResponse> unlikeReply(@PathVariable String id, Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        return ApiResponse.ok(likeService.unlike(userId, "reply", id));
    }

    @GetMapping("/likes/status")
    public ApiResponse<LikeStatusResponse> getStatus(
            @RequestParam("target_type") String targetType,
            @RequestParam("target_ids") String targetIds,
            Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        List<String> ids = Arrays.asList(targetIds.split(","));
        return ApiResponse.ok(likeService.getStatus(userId, targetType, ids));
    }
}
