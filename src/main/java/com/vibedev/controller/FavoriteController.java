package com.vibedev.controller;

import com.vibedev.common.ApiResponse;
import com.vibedev.common.PaginatedResponse;
import com.vibedev.dto.favorite.*;
import com.vibedev.security.SecurityHelper;
import com.vibedev.service.FavoriteService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
            @RequestParam(required = false) String folderId,
            Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        return ApiResponse.ok(favoriteService.listFavorites(userId, page, limit, folderId));
    }

    // ─── Folder endpoints ──────────────────────────────────

    @PostMapping("/favorites/folders")
    public ApiResponse<FolderResponse> createFolder(@RequestBody CreateFolderRequest body,
                                                     Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        return ApiResponse.ok(favoriteService.createFolder(userId, body.name()));
    }

    @GetMapping("/favorites/folders")
    public ApiResponse<List<FolderResponse>> listFolders(Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        return ApiResponse.ok(favoriteService.listFolders(userId));
    }

    @PutMapping("/favorites/folders/{id}")
    public ApiResponse<FolderResponse> updateFolder(@PathVariable String id,
                                                     @RequestBody UpdateFolderRequest body,
                                                     Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        return ApiResponse.ok(favoriteService.updateFolder(id, userId, body.name(), body.version()));
    }

    @DeleteMapping("/favorites/folders/{id}")
    public ApiResponse<Void> deleteFolder(@PathVariable String id,
                                           Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        favoriteService.deleteFolder(id, userId);
        return ApiResponse.ok();
    }

    @PutMapping("/favorites/folders/{id}/items")
    public ApiResponse<MoveFavoritesResponse> moveFavorites(@PathVariable String id,
                                                             @RequestBody MoveFavoritesRequest body,
                                                             Authentication auth) {
        String userId = SecurityHelper.getUserId(auth);
        String targetFolderId = body.targetFolderId() != null ? body.targetFolderId() : id;
        return ApiResponse.ok(favoriteService.moveFavorites(userId, body.postIds(), targetFolderId));
    }
}
