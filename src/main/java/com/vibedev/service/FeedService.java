package com.vibedev.service;

import com.vibedev.common.PaginatedResponse;
import com.vibedev.dto.board.*;
import com.vibedev.entity.*;
import com.vibedev.repository.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class FeedService {

    private final PostRepository postRepo;
    private final PostTagRepository postTagRepo;
    private final TagRepository tagRepo;
    private final UserRepository userRepo;
    private final BoardRepository boardRepo;
    private final BoardService boardService;

    public FeedService(PostRepository postRepo, PostTagRepository postTagRepo,
                       TagRepository tagRepo, UserRepository userRepo,
                       BoardRepository boardRepo, BoardService boardService) {
        this.postRepo = postRepo;
        this.postTagRepo = postTagRepo;
        this.tagRepo = tagRepo;
        this.userRepo = userRepo;
        this.boardRepo = boardRepo;
        this.boardService = boardService;
    }

    public PaginatedResponse<PostCard> feedTrending(int page, int limit) {
        if (page < 1) page = 1;
        if (limit < 1 || limit > 50) limit = 20;
        Pageable pageable = PageRequest.of(page - 1, limit);

        var postsPage = postRepo.findTrending(Instant.now().minusSeconds(86400), pageable);
        return toPostCardPage(postsPage, page, limit);
    }

    public PaginatedResponse<PostCard> feedRecommend(String userId, int page, int limit) {
        // V1.0: degraded to trending
        return feedTrending(page, limit);
    }

    public PaginatedResponse<PostCard> feedFollowing(String userId, int page, int limit) {
        // V1.0: return empty list (placeholder)
        return PaginatedResponse.of(Collections.emptyList(), 0, page, limit);
    }

    private PaginatedResponse<PostCard> toPostCardPage(
            org.springframework.data.domain.Page<Post> postsPage, int page, int limit) {
        var postIds = postsPage.getContent().stream().map(Post::getId).toList();
        var tagsByPostId = boardService.buildTagsByPostId(postIds);
        var usersById = buildUsersById(postsPage.getContent().stream()
                .map(Post::getAuthorId).distinct().toList());
        var boardsById = buildBoardsById(postsPage.getContent().stream()
                .map(Post::getBoardId).distinct().toList());

        var items = postsPage.getContent().stream()
                .map(p -> boardService.toPostCard(p,
                        tagsByPostId.getOrDefault(p.getId(), Collections.emptyList()),
                        usersById.get(p.getAuthorId()),
                        boardsById.getOrDefault(p.getBoardId(), "")))
                .toList();

        return PaginatedResponse.of(items, postsPage.getTotalElements(), page, limit);
    }

    private Map<String, User> buildUsersById(List<String> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        return userRepo.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private Map<String, String> buildBoardsById(List<String> boardIds) {
        if (boardIds.isEmpty()) return Collections.emptyMap();
        return boardRepo.findAllById(boardIds).stream()
                .collect(Collectors.toMap(Board::getId, Board::getName));
    }
}
