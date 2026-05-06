package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/posts")
public class BrowsingController {

    private final UserService userService;

    public BrowsingController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/{id}/view")
    public ApiResponse<Void> recordView(@PathVariable String id, Authentication auth) {
        String userId = auth.getPrincipal().toString();
        userService.recordBrowsing(userId, id);
        return ApiResponse.ok();
    }
}
