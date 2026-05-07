package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.common.PaginatedResponse;
import com.vibedev.dto.favorite.*;
import com.vibedev.dto.favorite.FavoriteItem.FavoritePostSummary;
import com.vibedev.entity.CollectionFolder;
import com.vibedev.entity.Favorite;
import com.vibedev.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepo;
    private final CollectionFolderRepository folderRepo;
    private final PostRepository postRepo;
    private final UserRepository userRepo;
    private final BoardRepository boardRepo;

    public FavoriteService(FavoriteRepository favoriteRepo, CollectionFolderRepository folderRepo,
                           PostRepository postRepo, UserRepository userRepo, BoardRepository boardRepo) {
        this.favoriteRepo = favoriteRepo;
        this.folderRepo = folderRepo;
        this.postRepo = postRepo;
        this.userRepo = userRepo;
        this.boardRepo = boardRepo;
    }

    // ─── Favorites list ────────────────────────────────────

    public PaginatedResponse<FavoriteItem> listFavorites(String userId, int page, int limit) {
        if (page < 1) page = 1;
        if (limit < 1 || limit > 50) limit = 20;
        var pageable = PageRequest.of(page - 1, limit);

        var favoritesPage = favoriteRepo.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        var items = favoritesPage.getContent().stream()
                .map(fav -> {
                    var post = postRepo.findById(fav.getPostId()).orElse(null);
                    FavoritePostSummary postSummary;
                    if (post == null || post.isDeleted()) {
                        postSummary = new FavoritePostSummary(
                                fav.getPostId(), "已删除的帖子", "原帖已删除",
                                "已注销用户", "", true);
                    } else {
                        var author = userRepo.findById(post.getAuthorId()).orElse(null);
                        String authorName = author != null ? author.getUsername() : "unknown";
                        String boardName = boardRepo.findById(post.getBoardId())
                                .map(b -> b.getName()).orElse("");
                        postSummary = new FavoritePostSummary(
                                post.getId(), post.getTitle(), post.getTitle(),
                                authorName, boardName, false);
                    }
                    return new FavoriteItem(
                            fav.getId(), fav.getUserId(), fav.getPostId(),
                            fav.getCollectionFolderId(), fav.getCreatedAt(), postSummary);
                })
                .toList();

        return PaginatedResponse.of(items, favoritesPage.getTotalElements(), page, limit);
    }

    // ─── Folder CRUD ───────────────────────────────────────

    @Transactional
    public FolderResponse createFolder(String userId, String name) {
        var user = userRepo.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        if (user.getLevel() < 3) {
            throw new BusinessException(ErrorCode.INSUFFICIENT_LEVEL,
                    "Lv.3 及以上用户才可创建收藏夹");
        }
        if (name == null || name.isBlank() || name.length() > 20) {
            throw new BusinessException(ErrorCode.VALIDATION_TITLE, "收藏夹名称需1-20个字符");
        }

        if (folderRepo.existsByUserIdAndName(userId, name)) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "已存在同名收藏夹");
        }

        var folder = new CollectionFolder();
        folder.setId(UUID.randomUUID().toString());
        folder.setUserId(userId);
        folder.setName(name.trim());
        folderRepo.save(folder);

        return new FolderResponse(folder.getId(), folder.getUserId(),
                folder.getName(), folder.getVersion(), folder.getCreatedAt());
    }

    public List<FolderResponse> listFolders(String userId) {
        return folderRepo.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(f -> new FolderResponse(f.getId(), f.getUserId(),
                        f.getName(), f.getVersion(), f.getCreatedAt()))
                .toList();
    }

    @Transactional
    public FolderResponse updateFolder(String folderId, String userId, String name, int version) {
        var folder = folderRepo.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "收藏夹不存在"));

        if (version != folder.getVersion()) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT.getCode(),
                    "收藏夹已被他人编辑，请刷新后重试");
        }
        if (name == null || name.isBlank() || name.length() > 20) {
            throw new BusinessException(ErrorCode.VALIDATION_TITLE, "收藏夹名称需1-20个字符");
        }

        folder.setName(name.trim());
        folderRepo.save(folder);

        return new FolderResponse(folder.getId(), folder.getUserId(),
                folder.getName(), folder.getVersion(), folder.getCreatedAt());
    }

    @Transactional
    public void deleteFolder(String folderId, String userId) {
        var folder = folderRepo.findByIdAndUserId(folderId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "收藏夹不存在"));

        // Move favorites in this folder back to default (null folder_id)
        var favorites = favoriteRepo.findByCollectionFolderId(folderId);
        for (var fav : favorites) {
            fav.setCollectionFolderId(null);
            favoriteRepo.save(fav);
        }

        folderRepo.delete(folder);
    }

    @Transactional
    public MoveFavoritesResponse moveFavorites(String userId, List<String> postIds, String targetFolderId) {
        // Validate target folder if not null
        if (targetFolderId != null) {
            folderRepo.findByIdAndUserId(targetFolderId, userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "收藏夹不存在"));
        }

        int moved = 0;
        for (String postId : postIds) {
            var fav = favoriteRepo.findByUserIdAndPostId(userId, postId);
            if (fav.isPresent()) {
                fav.get().setCollectionFolderId(targetFolderId);
                favoriteRepo.save(fav.get());
                moved++;
            }
        }

        if (moved == 0) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "未找到匹配的收藏记录");
        }

        return new MoveFavoritesResponse(moved);
    }
}
