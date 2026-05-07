package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.common.PaginatedResponse;
import com.vibedev.dto.board.PostCard;
import com.vibedev.service.FeedService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class FeedController {

    private final FeedService feedService;

    public FeedController(FeedService feedService) {
        this.feedService = feedService;
    }

    @GetMapping("/posts")
    public ApiResponse<PaginatedResponse<PostCard>> getFeed(
            @RequestParam(defaultValue = "trending") String tab,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication auth) {
        if (limit > 50) limit = 50;

        String userId = auth != null ? auth.getPrincipal().toString() : null;

        return switch (tab) {
            case "recommend" -> ApiResponse.ok(feedService.feedRecommend(userId, page, limit));
            case "following" -> ApiResponse.ok(feedService.feedFollowing(userId, page, limit));
            default -> ApiResponse.ok(feedService.feedTrending(page, limit));
        };
    }
}
