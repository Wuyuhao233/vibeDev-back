package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.common.PaginatedResponse;
import com.vibedev.dto.favorite.FavoriteItem;
import com.vibedev.security.SecurityHelper;
import com.vibedev.service.FavoriteService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class FavoriteController {

    private final FavoriteService favoriteService;

    public FavoriteController(FavoriteService favoriteService) {
        this.favoriteService = favoriteService;
    }

    @GetMapping("/favorites")
    public ApiResponse<PaginatedResponse<FavoriteItem>> listFavorites(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        return ApiResponse.ok(favoriteService.listFavorites(userId, page, limit));
    }
}
