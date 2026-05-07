package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.common.PaginatedResponse;
import com.vibedev.dto.reply.CreateReplyRequest;
import com.vibedev.dto.reply.ReplyResponse;
import com.vibedev.dto.reply.ReplyResponseMapper;
import com.vibedev.dto.reply.UpdateReplyRequest;
import com.vibedev.entity.Reply;
import com.vibedev.repository.PostRepository;
import com.vibedev.repository.ReplyRepository;
import com.vibedev.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class ReplyService {

    private static final Logger log = LoggerFactory.getLogger(ReplyService.class);
    private static final String IDEMPOTENCY_PREFIX = "idempotency:reply:";
    private static final String COOLDOWN_PREFIX = "reply:cooldown:";
    private static final String DAILY_KEY_PREFIX = "reply:daily:";
    private static final int COOLDOWN_SECONDS = 10;
    private static final int LV1_DAILY_REPLY_LIMIT = 15;
    private static final int IDEMPOTENCY_TTL = 60;

    private final ReplyRepository replyRepo;
    private final PostRepository postRepo;
    private final UserRepository userRepo;
    private final StringRedisTemplate redis;
    private final NotificationService notificationService;
    private final MuteService muteService;
    private final SensitiveWordService sensitiveWordService;

    public ReplyService(ReplyRepository replyRepo, PostRepository postRepo,
                        UserRepository userRepo, StringRedisTemplate redis,
                        NotificationService notificationService,
                        MuteService muteService,
                        SensitiveWordService sensitiveWordService) {
        this.replyRepo = replyRepo;
        this.postRepo = postRepo;
        this.userRepo = userRepo;
        this.redis = redis;
        this.notificationService = notificationService;
        this.muteService = muteService;
        this.sensitiveWordService = sensitiveWordService;
    }

    @Transactional
    public ReplyResponse create(String postId, CreateReplyRequest dto, String userId) {
        // 1. Idempotency check
        String idempotencyKey = IDEMPOTENCY_PREFIX + userId + ":" + dto.idempotencyKey();
        String cached = redis.opsForValue().get(idempotencyKey);
        if (cached != null) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "重复提交，操作已处理");
        }

        // 2. Validate content
        if (dto.content() == null || dto.content().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_CONTENT_EMPTY, "请输入回复内容");
        }

        // 3. Check user
        var user = userRepo.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));
        if (muteService.isUserBanned(userId)) {
            throw new BusinessException(ErrorCode.BANNED, "您已被禁言");
        }

        // 3b. Sensitive word check
        if (sensitiveWordService.hasSensitiveWord(dto.content())) {
            throw new BusinessException(ErrorCode.VALIDATION_SENSITIVE_WORD, "内容包含敏感词，请修改");
        }

        // 4. Check post exists
        var post = postRepo.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在"));

        // 5. Cooldown check (10s between replies on same post)
        String cooldownKey = COOLDOWN_PREFIX + userId + ":" + postId;
        if (Boolean.TRUE.equals(redis.hasKey(cooldownKey))) {
            Long ttl = redis.getExpire(cooldownKey, TimeUnit.SECONDS);
            long remaining = ttl != null && ttl > 0 ? ttl : 0;
            throw new BusinessException(ErrorCode.RATE_LIMITED,
                    "回复过于频繁，请" + remaining + "秒后再试");
        }

        // 6. Lv.1 daily reply limit
        if (user.getLevel() == 1) {
            String today = LocalDate.now(ZoneOffset.UTC).toString();
            String dailyKey = DAILY_KEY_PREFIX + userId + ":" + today;
            Long dailyCount = redis.opsForValue().increment(dailyKey);
            if (dailyCount == 1) {
                redis.expire(dailyKey, 86400, TimeUnit.SECONDS);
            }
            if (dailyCount != null && dailyCount > LV1_DAILY_REPLY_LIMIT) {
                throw new BusinessException(ErrorCode.RATE_LIMITED,
                        "今日回复次数已达上限（Lv.1 限" + LV1_DAILY_REPLY_LIMIT + "条/天）");
            }
        }

        // 7. Resolve parent reply and depth (V1.1: nested replies)
        String parentReplyId = null;
        int depth = 0;
        if (dto.parentReplyId() != null && !dto.parentReplyId().isBlank()) {
            var parentReply = replyRepo.findByIdAndIsDeletedFalse(dto.parentReplyId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "父回复不存在"));
            if (!parentReply.getPostId().equals(postId)) {
                throw new BusinessException(ErrorCode.VALIDATION_CONTENT_EMPTY, "父回复不属于该帖子");
            }
            parentReplyId = parentReply.getId();
            if (parentReply.getDepth() >= 2) {
                depth = parentReply.getDepth(); // max depth reached, stay at same level
            } else {
                depth = parentReply.getDepth() + 1;
            }
        }

        // 8. Create reply
        String replyId = UUID.randomUUID().toString();
        var reply = new Reply();
        reply.setId(replyId);
        reply.setContentMarkdown(dto.content());
        reply.setContentHtml(renderMarkdownToHtml(dto.content()));
        reply.setAuthorId(userId);
        reply.setPostId(postId);
        reply.setParentReplyId(parentReplyId);
        reply.setDepth(depth);
        reply.setAuditStatus("approved");
        replyRepo.save(reply);

        // 9. Increment post reply_count
        post.setReplyCount(post.getReplyCount() + 1);
        postRepo.save(post);

        // 9b. Send post_replied notification to post author (if not self-reply)
        if (!userId.equals(post.getAuthorId())) {
            String replierName = user.getUsername();
            String postTitle = post.getTitle();
            notificationService.create(
                    post.getAuthorId(),
                    "post_replied",
                    "帖子被回复",
                    replierName + " 回复了你的帖子《" + postTitle + "》",
                    "post",
                    postId
            );
        }

        // 10. Set cooldown
        redis.opsForValue().set(cooldownKey, "1", COOLDOWN_SECONDS, TimeUnit.SECONDS);

        // 11. Cache idempotency result
        try {
            redis.opsForValue().set(idempotencyKey, replyId, IDEMPOTENCY_TTL, TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("Failed to cache idempotency key: {}", e.getMessage());
        }

        return ReplyResponseMapper.from(reply, user, false);
    }

    public PaginatedResponse<ReplyResponse> listByPost(String postId, int page, int limit, String currentUserId) {
        if (page < 1) page = 1;
        if (limit < 1 || limit > 50) limit = 20;
        var pageable = PageRequest.of(page - 1, limit);

        // 1. Get paginated root replies (parent_reply_id IS NULL)
        var rootPage = replyRepo.findByPostIdAndIsDeletedFalseAndParentReplyIdIsNullOrderByCreatedAtAsc(postId, pageable);
        List<Reply> rootReplies = rootPage.getContent();

        // 2. Collect all relevant replies for tree building
        List<Reply> allReplies = new ArrayList<>(rootReplies);

        List<String> parentIds = rootReplies.stream().map(Reply::getId).toList();
        if (!parentIds.isEmpty()) {
            // Depth 1 children
            List<Reply> depth1 = replyRepo.findByPostIdAndIsDeletedFalseAndParentReplyIdInOrderByCreatedAtAsc(postId, parentIds);
            allReplies.addAll(depth1);

            // Depth 2 children
            List<String> depth1Ids = depth1.stream().map(Reply::getId).toList();
            if (!depth1Ids.isEmpty()) {
                List<Reply> depth2 = replyRepo.findByPostIdAndIsDeletedFalseAndParentReplyIdInOrderByCreatedAtAsc(postId, depth1Ids);
                allReplies.addAll(depth2);
            }
        }

        // 3. Batch load all authors
        var authorIds = allReplies.stream().map(Reply::getAuthorId).distinct().toList();
        var usersById = authorIds.isEmpty() ? Map.<String, com.vibedev.entity.User>of()
                : userRepo.findAllById(authorIds).stream()
                        .collect(Collectors.toMap(com.vibedev.entity.User::getId, u -> u));

        // 4. Build parent→children map
        Map<String, List<Reply>> childrenByParent = new HashMap<>();
        for (Reply r : allReplies) {
            if (r.getParentReplyId() != null) {
                childrenByParent.computeIfAbsent(r.getParentReplyId(), k -> new ArrayList<>()).add(r);
            }
        }

        // 5. Build root items with recursively assembled children
        var items = rootReplies.stream()
                .map(r -> buildTree(r, childrenByParent, usersById))
                .toList();

        return PaginatedResponse.of(items, rootPage.getTotalElements(), page, limit);
    }

    private ReplyResponse buildTree(Reply reply, Map<String, List<Reply>> childrenByParent,
                                    Map<String, com.vibedev.entity.User> usersById) {
        List<Reply> children = childrenByParent.getOrDefault(reply.getId(), List.of());
        List<ReplyResponse> childResponses = children.stream()
                .map(child -> buildTree(child, childrenByParent, usersById))
                .toList();
        var author = usersById.get(reply.getAuthorId());
        return ReplyResponseMapper.from(reply, author, false, childResponses);
    }

    @Transactional
    public ReplyResponse update(String replyId, UpdateReplyRequest dto, String userId, String role) {
        var reply = replyRepo.findByIdAndIsDeletedFalse(replyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "回复不存在"));

        checkEditPermission(reply, userId, role);

        // Optimistic lock
        if (dto.version() != reply.getVersion()) {
            throw new BusinessException(ErrorCode.VERSION_CONFLICT.getCode(), "内容已被他人编辑，请刷新后重试");
        }

        if (dto.content() == null || dto.content().isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_CONTENT_EMPTY, "请输入回复内容");
        }

        if (sensitiveWordService.hasSensitiveWord(dto.content())) {
            throw new BusinessException(ErrorCode.VALIDATION_SENSITIVE_WORD, "内容包含敏感词，请修改");
        }

        reply.setContentMarkdown(dto.content());
        reply.setContentHtml(renderMarkdownToHtml(dto.content()));
        reply.setLastEditedAt(Instant.now());
        replyRepo.save(reply);

        var author = userRepo.findById(reply.getAuthorId()).orElse(null);
        return ReplyResponseMapper.from(reply, author, false);
    }

    @Transactional
    public void softDelete(String replyId, String userId, String role) {
        var reply = replyRepo.findByIdAndIsDeletedFalse(replyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "回复不存在"));

        checkEditPermission(reply, userId, role);

        reply.setDeleted(true);
        reply.setDeletedAt(Instant.now());
        replyRepo.save(reply);
    }

    private void checkEditPermission(Reply reply, String userId, String role) {
        if (userId.equals(reply.getAuthorId())) return;
        if ("admin".equals(role)) return;
        if ("moderator".equals(role)) return;
        throw new BusinessException(ErrorCode.FORBIDDEN, "权限不足");
    }

    private String renderMarkdownToHtml(String markdown) {
        return "<p>" + markdown.replace("\n", "<br>") + "</p>";
    }
}
