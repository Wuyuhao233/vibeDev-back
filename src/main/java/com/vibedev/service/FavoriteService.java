package com.vibedev.service;

import com.vibedev.common.PaginatedResponse;
import com.vibedev.dto.favorite.FavoriteItem;
import com.vibedev.dto.favorite.FavoriteItem.FavoritePostSummary;
import com.vibedev.repository.BoardRepository;
import com.vibedev.repository.FavoriteRepository;
import com.vibedev.repository.PostRepository;
import com.vibedev.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepo;
    private final PostRepository postRepo;
    private final UserRepository userRepo;
    private final BoardRepository boardRepo;

    public FavoriteService(FavoriteRepository favoriteRepo, PostRepository postRepo,
                           UserRepository userRepo, BoardRepository boardRepo) {
        this.favoriteRepo = favoriteRepo;
        this.postRepo = postRepo;
        this.userRepo = userRepo;
        this.boardRepo = boardRepo;
    }

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
}
