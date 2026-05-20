package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.service.FollowService;
import com.vibedev.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class FollowController {

    private final FollowService followService;
    private final UserService userService;

    public FollowController(FollowService followService, UserService userService) {
        this.followService = followService;
        this.userService = userService;
    }

    @PostMapping("/{username}/follow")
    public ApiResponse<Map<String, Boolean>> follow(@PathVariable String username, Authentication auth) {
        String currentUserId = auth.getPrincipal().toString();
        String targetUserId = userService.resolveUserId(username);
        followService.followUser(currentUserId, targetUserId);
        return ApiResponse.ok(Map.of("following", true));
    }

    @DeleteMapping("/{username}/follow")
    public ApiResponse<Map<String, Boolean>> unfollow(@PathVariable String username, Authentication auth) {
        String currentUserId = auth.getPrincipal().toString();
        String targetUserId = userService.resolveUserId(username);
        followService.unfollowUser(currentUserId, targetUserId);
        return ApiResponse.ok(Map.of("following", false));
    }

    @GetMapping("/{username}/follow-status")
    public ApiResponse<Map<String, Boolean>> checkFollowStatus(@PathVariable String username, Authentication auth) {
        if (auth == null) {
            return ApiResponse.ok(Map.of("following", false));
        }
        String currentUserId = auth.getPrincipal().toString();
        String targetUserId = userService.resolveUserId(username);
        boolean following = followService.isFollowing(currentUserId, targetUserId);
        return ApiResponse.ok(Map.of("following", following));
    }
}
