package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.dto.board.TagItem;
import com.vibedev.service.TagService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @GetMapping
    public ApiResponse<List<TagItem>> getAllTags() {
        return ApiResponse.ok(tagService.getAllTags());
    }

    @GetMapping("/followed")
    public ApiResponse<List<TagItem>> getFollowedTags(Authentication auth) {
        String userId = auth.getPrincipal().toString();
        return ApiResponse.ok(tagService.getFollowedTags(userId));
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
