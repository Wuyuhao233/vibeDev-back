package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.dto.admin.post.*;
import com.vibedev.dto.post.EssenceResponse;
import com.vibedev.dto.post.PinResponse;
import com.vibedev.entity.Post;
import com.vibedev.repository.BoardRepository;
import com.vibedev.repository.PostRepository;
import com.vibedev.repository.PostTagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class PostManageService {

    private static final Logger log = LoggerFactory.getLogger(PostManageService.class);

    private final PostRepository postRepo;
    private final BoardRepository boardRepo;
    private final PostTagRepository postTagRepo;
    private final PostService postService;

    public PostManageService(PostRepository postRepo, BoardRepository boardRepo,
                              PostTagRepository postTagRepo, PostService postService) {
        this.postRepo = postRepo;
        this.boardRepo = boardRepo;
        this.postTagRepo = postTagRepo;
        this.postService = postService;
    }

    public com.vibedev.common.PaginatedResponse<AdminPostItem> listPosts(
            String boardId, String status, String search, int page, int limit) {
        if (page < 1) page = 1;
        if (limit < 1 || limit > 100) limit = 20;

        var pageable = PageRequest.of(page - 1, limit);
        var result = postRepo.findPostsForAdmin(
                boardId != null && !boardId.isBlank() ? boardId : null,
                status != null && !status.isBlank() ? status : "active",
                search != null && !search.isBlank() ? search : null,
                pageable);

        var items = result.getContent().stream()
                .map(AdminPostItem::from)
                .toList();

        return com.vibedev.common.PaginatedResponse.of(items, result.getTotalElements(), page, limit);
    }

    @Transactional
    public void forceDelete(String postId, String adminId, String role) {
        // Allow finding even deleted posts
        var post = postRepo.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在"));

        if (post.isDeleted()) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "帖子已被删除");
        }

        post.setDeleted(true);
        post.setDeletedAt(Instant.now());
        post.setDeletedBy(adminId);
        postRepo.save(post);
    }

    @Transactional
    public void movePost(String postId, String targetBoardId) {
        var post = postRepo.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在"));

        // Validate target board exists
        boardRepo.findById(targetBoardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "目标版块不存在"));

        if (post.getBoardId().equals(targetBoardId)) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "帖子已在该版块下");
        }

        // Move post
        post.setBoardId(targetBoardId);

        // Clear tags that don't exist in target board (tags are board-specific)
        var targetBoardTags = postTagRepo.findByPostId(postId);
        if (!targetBoardTags.isEmpty()) {
            // Remove all post-tag associations — admins can re-tag if needed
            for (var pt : targetBoardTags) {
                postTagRepo.delete(pt);
            }
        }

        postRepo.save(post);
    }

    @Transactional
    public PinResponse togglePin(String postId, String pinType, String userId, String role) {
        return postService.togglePin(postId, pinType, userId, role);
    }

    @Transactional
    public com.vibedev.dto.post.EssenceResponse toggleEssence(String postId, String userId, String role) {
        return postService.toggleEssence(postId, userId, role);
    }
}
