package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.common.PaginatedResponse;
import com.vibedev.dto.admin.post.*;
import com.vibedev.dto.post.EssenceResponse;
import com.vibedev.dto.post.PinResponse;
import com.vibedev.security.SecurityHelper;
import com.vibedev.service.PostManageService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminPostController {

    private final PostManageService postManageService;

    public AdminPostController(PostManageService postManageService) {
        this.postManageService = postManageService;
    }

    @GetMapping("/posts")
    public ApiResponse<PaginatedResponse<AdminPostItem>> listPosts(
            @RequestParam(required = false) String boardId,
            @RequestParam(defaultValue = "active") String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponse.ok(postManageService.listPosts(boardId, status, search, page, limit));
    }

    @DeleteMapping("/posts/{id}")
    public ApiResponse<Void> forceDelete(@PathVariable String id, Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        String role = SecurityHelper.getRole(auth);
        postManageService.forceDelete(id, userId, role);
        return ApiResponse.ok();
    }

    @PutMapping("/posts/{id}/move")
    public ApiResponse<Void> movePost(@PathVariable String id,
                                       @Valid @RequestBody MovePostRequest dto) {
        postManageService.movePost(id, dto.targetBoardId());
        return ApiResponse.ok();
    }

    @PostMapping("/posts/{id}/pin")
    public ApiResponse<PinResponse> togglePin(@PathVariable String id,
                                              @Valid @RequestBody PinPostRequest dto,
                                              Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        String role = SecurityHelper.getRole(auth);
        return ApiResponse.ok(postManageService.togglePin(id, dto.pinType(), userId, role));
    }

    @PostMapping("/posts/{id}/essence")
    public ApiResponse<EssenceResponse> toggleEssence(@PathVariable String id,
                                                       @RequestBody(required = false) EssencePostRequest dto,
                                                       Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        String role = SecurityHelper.getRole(auth);
        return ApiResponse.ok(postManageService.toggleEssence(id, userId, role));
    }
}
