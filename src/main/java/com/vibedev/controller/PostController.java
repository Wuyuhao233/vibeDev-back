package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.common.PaginatedResponse;
import com.vibedev.dto.post.*;
import com.vibedev.dto.reply.CreateReplyRequest;
import com.vibedev.dto.reply.ReplyResponse;
import com.vibedev.dto.reply.UpdateReplyRequest;
import com.vibedev.security.SecurityHelper;
import com.vibedev.service.PostService;
import com.vibedev.service.ReplyService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class PostController {

    private final PostService postService;
    private final ReplyService replyService;

    public PostController(PostService postService, ReplyService replyService) {
        this.postService = postService;
        this.replyService = replyService;
    }

    @PostMapping("/posts")
    public ApiResponse<PostDetailResponse> createPost(@RequestBody CreatePostRequest dto,
                                                       Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        return ApiResponse.ok(postService.create(dto, userId));
    }

    @GetMapping("/posts/{id}")
    public ApiResponse<PostDetailResponse> getPost(@PathVariable String id,
                                                    Authentication auth) {
        String currentUserId = SecurityHelper.getUserId(auth);
        return ApiResponse.ok(postService.getById(id, currentUserId));
    }

    @PutMapping("/posts/{id}")
    public ApiResponse<PostDetailResponse> updatePost(@PathVariable String id,
                                                       @RequestBody UpdatePostRequest dto,
                                                       Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        String role = SecurityHelper.getRole(auth);
        return ApiResponse.ok(postService.update(id, dto, userId, role));
    }

    @DeleteMapping("/posts/{id}")
    public ApiResponse<Void> deletePost(@PathVariable String id,
                                         Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        String role = SecurityHelper.getRole(auth);
        postService.softDelete(id, userId, role);
        return ApiResponse.ok();
    }

    @PostMapping("/posts/{id}/pin")
    public ApiResponse<PinResponse> pinPost(@PathVariable String id,
                                             @RequestBody PinRequest dto,
                                             Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        String role = SecurityHelper.getRole(auth);
        return ApiResponse.ok(postService.togglePin(id, dto.pinType(), userId, role));
    }

    @DeleteMapping("/posts/{id}/pin")
    public ApiResponse<Void> unpinPost(@PathVariable String id,
                                        Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        String role = SecurityHelper.getRole(auth);
        postService.unpin(id, userId, role);
        return ApiResponse.ok();
    }

    @PostMapping("/posts/{id}/essence")
    public ApiResponse<EssenceResponse> essencePost(@PathVariable String id,
                                                      Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        String role = SecurityHelper.getRole(auth);
        return ApiResponse.ok(postService.toggleEssence(id, userId, role));
    }

    @DeleteMapping("/posts/{id}/essence")
    public ApiResponse<Void> unessencePost(@PathVariable String id,
                                            Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        String role = SecurityHelper.getRole(auth);
        postService.unessence(id, userId, role);
        return ApiResponse.ok();
    }

    @PostMapping("/posts/{id}/collect")
    public ApiResponse<CollectToggleResponse> collectPost(@PathVariable String id,
                                                           Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        return ApiResponse.ok(postService.collect(id, userId));
    }

    @DeleteMapping("/posts/{id}/collect")
    public ApiResponse<CollectToggleResponse> uncollectPost(@PathVariable String id,
                                                             Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        return ApiResponse.ok(postService.uncollect(id, userId));
    }

    @GetMapping("/posts/{id}/share-card")
    public ApiResponse<ShareCardResponse> shareCard(@PathVariable String id) {
        return ApiResponse.ok(postService.getShareCard(id));
    }

    // ─── Replies ──────────────────────────────────────────

    @GetMapping("/posts/{id}/replies")
    public ApiResponse<PaginatedResponse<ReplyResponse>> listReplies(
            @PathVariable String id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication auth) {
        String currentUserId = SecurityHelper.getUserId(auth);
        return ApiResponse.ok(replyService.listByPost(id, page, limit, currentUserId));
    }

    @PostMapping("/posts/{id}/replies")
    public ApiResponse<ReplyResponse> createReply(@PathVariable String id,
                                                    @RequestBody CreateReplyRequest dto,
                                                    Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        return ApiResponse.ok(replyService.create(id, dto, userId));
    }

    @PutMapping("/replies/{id}")
    public ApiResponse<ReplyResponse> updateReply(@PathVariable String id,
                                                    @RequestBody UpdateReplyRequest dto,
                                                    Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        String role = SecurityHelper.getRole(auth);
        return ApiResponse.ok(replyService.update(id, dto, userId, role));
    }

    @DeleteMapping("/replies/{id}")
    public ApiResponse<Void> deleteReply(@PathVariable String id,
                                          Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        String role = SecurityHelper.getRole(auth);
        replyService.softDelete(id, userId, role);
        return ApiResponse.ok();
    }
}
