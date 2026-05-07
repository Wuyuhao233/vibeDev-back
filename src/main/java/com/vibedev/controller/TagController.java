package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.service.TagService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @PostMapping("/{id}/follow")
    public ApiResponse<Void> follow(@PathVariable String id, Authentication auth) {
        String userId = auth.getPrincipal().toString();
        tagService.followTag(userId, id);
        return new ApiResponse<>(0, "关注成功", null);
    }

    @DeleteMapping("/{id}/follow")
    public ApiResponse<Void> unfollow(@PathVariable String id, Authentication auth) {
        String userId = auth.getPrincipal().toString();
        tagService.unfollowTag(userId, id);
        return new ApiResponse<>(0, "已取消关注", null);
    }
}
