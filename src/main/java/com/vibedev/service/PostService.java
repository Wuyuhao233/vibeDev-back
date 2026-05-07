package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.common.PaginatedResponse;
import com.vibedev.dto.board.TagItem;
import com.vibedev.dto.post.*;
import com.vibedev.dto.user.UserSummary;
import com.vibedev.entity.*;
import com.vibedev.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class PostService {

    private static final Logger log = LoggerFactory.getLogger(PostService.class);
    private static final String IDEMPOTENCY_PREFIX = "idempotency:post:";
    private static final String RATE_KEY_PREFIX = "post:rate:";
    private static final String DAILY_KEY_PREFIX = "post:daily:";
    private static final int RATE_LIMIT_PER_MINUTE = 3;
    private static final int LV1_DAILY_LIMIT = 5;
    private static final int LV2_DAILY_LIMIT = 10;
    private static final int IDEMPOTENCY_TTL = 60;

    private final PostRepository postRepo;
    private final PostTagRepository postTagRepo;
    private final TagRepository tagRepo;
    private final UserRepository userRepo;
    private final BoardRepository boardRepo;
    private final FavoriteRepository favoriteRepo;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    public PostService(PostRepository postRepo, PostTagRepository postTagRepo,
                       TagRepository tagRepo, UserRepository userRepo,
                       BoardRepository boardRepo, FavoriteRepository favoriteRepo,
                       org.springframework.data.redis.core.StringRedisTemplate redis,
                       com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.postRepo = postRepo;
        this.postTagRepo = postTagRepo;
        this.tagRepo = tagRepo;
        this.userRepo = userRepo;
        this.boardRepo = boardRepo;
        this.favoriteRepo = favoriteRepo;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PostDetailResponse create(CreatePostRequest dto, String userId) {
        // 1. Idempotency check
        String idempotencyKey = IDEMPOTENCY_PREFIX + userId + ":" + dto.idempotencyKey();
        String cached = redis.opsForValue().get(idempotencyKey);
        if (cached != null) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "重复提交，操作已处理");
        }

        // 2. Validate title length
        if (dto.title().length() < 5 || dto.title().length() > 100) {
            throw new BusinessException(ErrorCode.VALIDATION_TITLE, "标题需5-100个字符");
        }
        if (dto.tagIds() == null || dto.tagIds().isEmpty() || dto.tagIds().size() > 3) {
            throw new BusinessException(ErrorCode.VALIDATION_TAGS, "请选择1-3个子标签");
        }
        if (dto.content() == null || dto.content().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_CONTENT_EMPTY, "请输入内容");
        }

        // 3. Check user
        var user = userRepo.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        if (user.isBanned()) {
            throw new BusinessException(ErrorCode.BANNED, "您已被禁言");
        }

        // 4. Check board exists
        boardRepo.findByIdAndIsDeletedFalse(dto.boardId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "版块不存在"));

        // 5. Rate limit per minute
        String rateKey = RATE_KEY_PREFIX + userId + ":1min";
        Long rateCount = redis.opsForValue().increment(rateKey);
        if (rateCount == 1) {
            redis.expire(rateKey, 60, TimeUnit.SECONDS);
        }
        if (rateCount != null && rateCount > RATE_LIMIT_PER_MINUTE) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "操作过于频繁，请稍后再试");
        }

        // 6. Daily post limit by level
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String dailyKey = DAILY_KEY_PREFIX + userId + ":" + today;
        Long dailyCount = redis.opsForValue().increment(dailyKey);
        if (dailyCount == 1) {
            redis.expire(dailyKey, 86400, TimeUnit.SECONDS);
        }
        int dailyLimit = user.getLevel() == 1 ? LV1_DAILY_LIMIT :
                         user.getLevel() == 2 ? LV2_DAILY_LIMIT : Integer.MAX_VALUE;
        if (dailyLimit < Integer.MAX_VALUE && dailyCount != null && dailyCount > dailyLimit) {
            throw new BusinessException(ErrorCode.RATE_LIMITED,
                    "今日发帖次数已达上限（Lv." + user.getLevel() + " 限" + dailyLimit + "帖/天）");
        }

        // 7. Check title duplicate in same board
        int titleCount = postRepo.countByTitleAndBoardIdAndIsDeletedFalse(dto.title(), dto.boardId());
        if (titleCount > 0) {
            throw new BusinessException(ErrorCode.VALIDATION_TITLE_DUPLICATE,
                    "该版块已存在相同标题的帖子");
        }

        // 8. Create post
        String postId = UUID.randomUUID().toString();
        var post = new Post();
        post.setId(postId);
        post.setTitle(dto.title());
        post.setContentMarkdown(dto.content());
        post.setContentHtml(renderMarkdownToHtml(dto.content()));
        post.setAuthorId(userId);
        post.setBoardId(dto.boardId());
        post.setCoverImageUrl(dto.coverImageUrl());
        post.setAuditStatus("approved");
        post.setContentLength(dto.content().length());
        post.setHasCodeBlock(dto.content().contains("```"));
        postRepo.save(post);

        // 9. Create post_tags
        for (String tagId : dto.tagIds()) {
            var pt = new PostTag();
            pt.setId(UUID.randomUUID().toString());
            pt.setPostId(postId);
            pt.setTagId(tagId);
            postTagRepo.save(pt);
        }

        // 10. Award points (+5)
        user.setPoints(user.getPoints() + 5);
        userRepo.save(user);

        // 11. Cache idempotency result
        try {
            redis.opsForValue().set(idempotencyKey, postId, IDEMPOTENCY_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to cache idempotency key: {}", e.getMessage());
        }

        return toPostDetail(post, user, userId);
    }

    public PostDetailResponse getById(String postId, String currentUserId) {
        var post = postRepo.findById(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在"));

        var author = userRepo.findById(post.getAuthorId()).orElse(null);
        var currentUser = currentUserId != null ? userRepo.findById(currentUserId).orElse(null) : null;
        String role = currentUser != null ? currentUser.getRole() : "user";

        // Check visibility
        if (post.isDeleted()) {
            boolean canView = currentUserId != null &&
                    (currentUserId.equals(post.getAuthorId()) ||
                     "admin".equals(role) || "moderator".equals(role));
            if (!canView) {
                throw new BusinessException(ErrorCode.POST_DELETED, "该帖子已被删除");
            }
        }
        if ("rejected".equals(post.getAuditStatus())) {
            boolean canView = currentUserId != null &&
                    (currentUserId.equals(post.getAuthorId()) ||
                     "admin".equals(role) || "moderator".equals(role));
            if (!canView) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "内容不可见");
            }
        }

        return toPostDetail(post, author, currentUserId);
    }

    @Transactional
    public PostDetailResponse update(String postId, UpdatePostRequest dto, String userId, String role) {
        var post = postRepo.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在"));

        // Permission check
        checkEditPermission(post, userId, role);

        // Optimistic lock
        if (dto.version() != post.getVersion()) {
            var detail = new HashMap<String, Object>();
            detail.put("current_version", post.getVersion());
            throw new BusinessException(ErrorCode.VERSION_CONFLICT.getCode(), "内容已被他人编辑，请刷新后重试");
        }

        if (dto.title() != null) {
            if (dto.title().length() < 5 || dto.title().length() > 100) {
                throw new BusinessException(ErrorCode.VALIDATION_TITLE, "标题需5-100个字符");
            }
            post.setTitle(dto.title());
        }
        if (dto.content() != null) {
            if (dto.content().isBlank()) {
                throw new BusinessException(ErrorCode.VALIDATION_CONTENT_EMPTY, "请输入内容");
            }
            post.setContentMarkdown(dto.content());
            post.setContentHtml(renderMarkdownToHtml(dto.content()));
            post.setContentLength(dto.content().length());
            post.setHasCodeBlock(dto.content().contains("```"));
        }
        if (dto.coverImageUrl() != null || (dto.title() != null && dto.coverImageUrl() == null)) {
            post.setCoverImageUrl(dto.coverImageUrl());
        }
        if (dto.tagIds() != null && !dto.tagIds().isEmpty()) {
            if (dto.tagIds().size() > 3) {
                throw new BusinessException(ErrorCode.VALIDATION_TAGS, "请选择1-3个子标签");
            }
            // Replace tags
            var existing = postTagRepo.findByPostId(postId);
            postTagRepo.deleteAll(existing);
            for (String tagId : dto.tagIds()) {
                var pt = new PostTag();
                pt.setId(UUID.randomUUID().toString());
                pt.setPostId(postId);
                pt.setTagId(tagId);
                postTagRepo.save(pt);
            }
        }

        post.setLastEditedAt(Instant.now());
        postRepo.save(post);

        var author = userRepo.findById(post.getAuthorId()).orElse(null);
        return toPostDetail(post, author, userId);
    }

    @Transactional
    public void softDelete(String postId, String userId, String role) {
        var post = postRepo.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在"));

        checkModeratePermission(post, userId, role);

        post.setDeleted(true);
        post.setDeletedAt(Instant.now());
        post.setDeletedBy(userId);
        postRepo.save(post);

        // If admin/moderator deletes someone else's post, deduct points (-10)
        if (!userId.equals(post.getAuthorId())) {
            var author = userRepo.findById(post.getAuthorId()).orElse(null);
            if (author != null) {
                author.setPoints(Math.max(0, author.getPoints() - 10));
                userRepo.save(author);
            }
        } else {
            // Self-delete: deduct the +5 posting reward
            var author = userRepo.findById(post.getAuthorId()).orElse(null);
            if (author != null) {
                author.setPoints(Math.max(0, author.getPoints() - 5));
                userRepo.save(author);
            }
        }
    }

    @Transactional
    public PinResponse togglePin(String postId, String pinType, String userId, String role) {
        var post = postRepo.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在"));

        if (!"admin".equals(role) && !"moderator".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "权限不足");
        }

        if ("global".equals(pinType) && !"admin".equals(role)) {
            throw new BusinessException(ErrorCode.NOT_MODERATOR_OF_BOARD, "仅管理员可全站置顶");
        }

        if (post.isPinned() && post.getPinType().equals(pinType)) {
            // Unpin
            post.setPinned(false);
            post.setPinType("none");
        } else {
            post.setPinned(true);
            post.setPinType(pinType);
        }
        postRepo.save(post);
        return new PinResponse(post.isPinned(), post.getPinType());
    }

    @Transactional
    public void unpin(String postId, String userId, String role) {
        var post = postRepo.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在"));

        if (!"admin".equals(role) && !"moderator".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "权限不足");
        }

        post.setPinned(false);
        post.setPinType("none");
        postRepo.save(post);
    }

    @Transactional
    public EssenceResponse toggleEssence(String postId, String userId, String role) {
        var post = postRepo.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在"));

        if (!"admin".equals(role) && !"moderator".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "权限不足");
        }

        boolean wasEssence = post.isEssence();
        post.setEssence(!wasEssence);
        postRepo.save(post);

        // First time essence: +20 points (one-time reward)
        if (!wasEssence) {
            var author = userRepo.findById(post.getAuthorId()).orElse(null);
            if (author != null) {
                author.setPoints(author.getPoints() + 20);
                userRepo.save(author);
            }
        }
        // Un-essence does NOT deduct points

        return new EssenceResponse(post.isEssence());
    }

    @Transactional
    public void unessence(String postId, String userId, String role) {
        var post = postRepo.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在"));

        if (!"admin".equals(role) && !"moderator".equals(role)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "权限不足");
        }

        post.setEssence(false);
        postRepo.save(post);
    }

    @Transactional
    public CollectToggleResponse collect(String postId, String userId) {
        var post = postRepo.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在"));
        if (post.isDeleted()) {
            throw new BusinessException(ErrorCode.POST_DELETED, "该帖子已被删除");
        }

        var existing = favoriteRepo.findByUserIdAndPostId(userId, postId);
        if (existing.isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "已收藏该帖子");
        }

        var fav = new Favorite();
        fav.setId(UUID.randomUUID().toString());
        fav.setUserId(userId);
        fav.setPostId(postId);
        favoriteRepo.save(fav);

        post.setCollectCount(post.getCollectCount() + 1);
        postRepo.save(post);

        return new CollectToggleResponse(true, post.getCollectCount());
    }

    @Transactional
    public CollectToggleResponse uncollect(String postId, String userId) {
        var post = postRepo.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在"));

        var existing = favoriteRepo.findByUserIdAndPostId(userId, postId);
        if (existing.isEmpty()) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "未收藏该帖子");
        }

        favoriteRepo.delete(existing.get());
        post.setCollectCount(Math.max(0, post.getCollectCount() - 1));
        postRepo.save(post);

        return new CollectToggleResponse(false, post.getCollectCount());
    }

    public ShareCardResponse getShareCard(String postId) {
        var post = postRepo.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在"));

        String description = "";
        if (post.getContentMarkdown() != null) {
            String plain = post.getContentMarkdown()
                    .replaceAll("```[\\s\\S]*?```", " ")
                    .replaceAll("`[^`]*`", " ")
                    .replaceAll("!\\[.*?]\\(.*?\\)", " ")
                    .replaceAll("\\[([^]]*)]\\(.*?\\)", "$1")
                    .replaceAll("[*#>`~|_\\-]+", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            description = plain.length() > 200 ? plain.substring(0, 200) : plain;
        }

        var author = userRepo.findById(post.getAuthorId()).orElse(null);
        String authorName = author != null ? author.getUsername() : "unknown";

        return new ShareCardResponse(
                post.getTitle(),
                description,
                post.getCoverImageUrl(),
                authorName,
                "/post/" + postId
        );
    }

    // ─── Helpers ──────────────────────────────────────────

    private PostDetailResponse toPostDetail(Post p, User author, String currentUserId) {
        var tags = buildTagsForPost(p.getId());
        var authorDto = author != null
                ? UserSummary.from(author)
                : new UserSummary("unknown", "unknown", "未知用户", "", 1, "user");
        String boardName = boardRepo.findById(p.getBoardId())
                .map(Board::getName).orElse("");

        boolean isCollected = false;
        boolean isLiked = false;
        if (currentUserId != null) {
            isCollected = favoriteRepo.findByUserIdAndPostId(currentUserId, p.getId()).isPresent();
            // isLiked would check a LikeRepository - placeholder for now
        }

        return new PostDetailResponse(
                p.getId(), p.getTitle(), p.getContentMarkdown(), p.getContentHtml(),
                p.getAuthorId(), p.getBoardId(), boardName, tags, p.getCoverImageUrl(),
                authorDto, isCollected, isLiked,
                p.getLikeCount(), p.getReplyCount(), p.getCollectCount(),
                p.getShareCount(), p.isPinned(), p.getPinType(), p.isEssence(),
                p.getAuditStatus(), p.isDeleted(), p.getVersion(),
                p.getCreatedAt(), p.getUpdatedAt(), p.getLastEditedAt()
        );
    }

    private List<TagItem> buildTagsForPost(String postId) {
        var postTags = postTagRepo.findByPostId(postId);
        if (postTags.isEmpty()) return List.of();
        var tagIds = postTags.stream().map(PostTag::getTagId).toList();
        return tagRepo.findAllById(tagIds).stream()
                .map(t -> new TagItem(t.getId(), t.getName(), t.getBoardId(), t.getSortOrder(), t.getPostCount()))
                .toList();
    }

    private void checkEditPermission(Post post, String userId, String role) {
        if (userId.equals(post.getAuthorId())) return;
        if ("admin".equals(role)) return;
        if ("moderator".equals(role)) return;
        throw new BusinessException(ErrorCode.FORBIDDEN, "权限不足");
    }

    private void checkModeratePermission(Post post, String userId, String role) {
        if (userId.equals(post.getAuthorId())) return;
        if ("admin".equals(role)) return;
        if ("moderator".equals(role)) return;
        throw new BusinessException(ErrorCode.FORBIDDEN, "权限不足");
    }

    private String renderMarkdownToHtml(String markdown) {
        // Basic markdown -> HTML conversion
        // In production, use a proper library like flexmark or commonmark-java
        return "<p>" + markdown.replace("\n", "<br>") + "</p>";
    }
}
