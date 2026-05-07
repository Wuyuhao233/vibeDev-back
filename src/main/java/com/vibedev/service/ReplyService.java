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

    public ReplyService(ReplyRepository replyRepo, PostRepository postRepo,
                        UserRepository userRepo, StringRedisTemplate redis) {
        this.replyRepo = replyRepo;
        this.postRepo = postRepo;
        this.userRepo = userRepo;
        this.redis = redis;
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
        if (user.isBanned()) {
            throw new BusinessException(ErrorCode.BANNED, "您已被禁言");
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

        // 7. Create reply (V1.0: flat, depth=0)
        String replyId = UUID.randomUUID().toString();
        var reply = new Reply();
        reply.setId(replyId);
        reply.setContentMarkdown(dto.content());
        reply.setContentHtml(renderMarkdownToHtml(dto.content()));
        reply.setAuthorId(userId);
        reply.setPostId(postId);
        reply.setParentReplyId(null);
        reply.setDepth(0);
        reply.setAuditStatus("approved");
        replyRepo.save(reply);

        // 8. Increment post reply_count
        post.setReplyCount(post.getReplyCount() + 1);
        postRepo.save(post);

        // 9. Set cooldown
        redis.opsForValue().set(cooldownKey, "1", COOLDOWN_SECONDS, TimeUnit.SECONDS);

        // 10. Cache idempotency result
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

        var repliesPage = replyRepo.findByPostIdAndIsDeletedFalseOrderByCreatedAtAsc(postId, pageable);

        var authorIds = repliesPage.getContent().stream()
                .map(Reply::getAuthorId).distinct().toList();
        var usersById = authorIds.isEmpty() ? Map.<String, com.vibedev.entity.User>of()
                : userRepo.findAllById(authorIds).stream()
                        .collect(Collectors.toMap(com.vibedev.entity.User::getId, u -> u));

        var items = repliesPage.getContent().stream()
                .map(r -> {
                    var author = usersById.get(r.getAuthorId());
                    // V1.0: all replies are flat, child_replies is always empty
                    return ReplyResponseMapper.from(r, author, false);
                })
                .toList();

        return PaginatedResponse.of(items, repliesPage.getTotalElements(), page, limit);
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
