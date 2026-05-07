package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.dto.like.LikeStatusItem;
import com.vibedev.dto.like.LikeStatusResponse;
import com.vibedev.dto.like.LikeToggleResponse;
import com.vibedev.entity.Like;
import com.vibedev.entity.Post;
import com.vibedev.entity.Reply;
import com.vibedev.repository.LikeRepository;
import com.vibedev.repository.PostRepository;
import com.vibedev.repository.ReplyRepository;
import com.vibedev.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class LikeService {

    private final LikeRepository likeRepo;
    private final PostRepository postRepo;
    private final ReplyRepository replyRepo;
    private final UserRepository userRepo;
    private final NotificationService notificationService;

    public LikeService(LikeRepository likeRepo, PostRepository postRepo,
                       ReplyRepository replyRepo, UserRepository userRepo,
                       NotificationService notificationService) {
        this.likeRepo = likeRepo;
        this.postRepo = postRepo;
        this.replyRepo = replyRepo;
        this.userRepo = userRepo;
        this.notificationService = notificationService;
    }

    @Transactional
    public LikeToggleResponse like(String userId, String targetType, String targetId) {
        var existing = likeRepo.findByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId);
        if (existing.isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "已点赞，不可重复点赞");
        }

        var like = new Like();
        like.setId(UUID.randomUUID().toString());
        like.setUserId(userId);
        like.setTargetType(targetType);
        like.setTargetId(targetId);
        likeRepo.save(like);

        int newCount = incrementTargetCount(targetType, targetId);

        String targetAuthorId = getTargetAuthorId(targetType, targetId);
        if (targetAuthorId != null && !targetAuthorId.equals(userId)) {
            var liker = userRepo.findById(userId).orElse(null);
            String likerName = liker != null ? liker.getUsername() : "unknown";
            String targetLabel = "post".equals(targetType) ? "帖子" : "回复";
            notificationService.create(
                    targetAuthorId,
                    "received_like",
                    "收到点赞",
                    likerName + " 赞了你的" + targetLabel,
                    "post".equals(targetType) ? "post" : "reply",
                    targetId
            );
        }

        return new LikeToggleResponse(true, newCount);
    }

    @Transactional
    public LikeToggleResponse unlike(String userId, String targetType, String targetId) {
        var existing = likeRepo.findByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId);
        if (existing.isPresent()) {
            likeRepo.delete(existing.get());
            int newCount = decrementTargetCount(targetType, targetId);
            return new LikeToggleResponse(false, newCount);
        }

        // Idempotent: return current count even if no like existed
        int currentCount = getTargetCount(targetType, targetId);
        return new LikeToggleResponse(false, currentCount);
    }

    public LikeStatusResponse getStatus(String userId, String targetType, List<String> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return new LikeStatusResponse(List.of());
        }
        if (targetIds.size() > 100) {
            targetIds = targetIds.subList(0, 100);
        }

        List<LikeStatusItem> items = new ArrayList<>();
        for (String targetId : targetIds) {
            boolean isLiked = userId != null && likeRepo.existsByUserIdAndTargetTypeAndTargetId(userId, targetType, targetId);
            int count = likeRepo.countByTargetTypeAndTargetId(targetType, targetId);
            items.add(new LikeStatusItem(targetType, targetId, isLiked, count));
        }
        return new LikeStatusResponse(items);
    }

    private int incrementTargetCount(String targetType, String targetId) {
        if ("post".equals(targetType)) {
            var post = postRepo.findById(targetId).orElse(null);
            if (post == null) throw new BusinessException(ErrorCode.NOT_FOUND, "目标内容不存在");
            post.setLikeCount(post.getLikeCount() + 1);
            postRepo.save(post);
            return post.getLikeCount();
        }
        if ("reply".equals(targetType)) {
            var reply = replyRepo.findById(targetId).orElse(null);
            if (reply == null) throw new BusinessException(ErrorCode.NOT_FOUND, "目标内容不存在");
            reply.setLikeCount(reply.getLikeCount() + 1);
            replyRepo.save(reply);
            return reply.getLikeCount();
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "目标内容不存在");
    }

    private int decrementTargetCount(String targetType, String targetId) {
        if ("post".equals(targetType)) {
            var post = postRepo.findById(targetId).orElse(null);
            if (post == null) throw new BusinessException(ErrorCode.NOT_FOUND, "目标内容不存在");
            post.setLikeCount(Math.max(0, post.getLikeCount() - 1));
            postRepo.save(post);
            return post.getLikeCount();
        }
        if ("reply".equals(targetType)) {
            var reply = replyRepo.findById(targetId).orElse(null);
            if (reply == null) throw new BusinessException(ErrorCode.NOT_FOUND, "目标内容不存在");
            reply.setLikeCount(Math.max(0, reply.getLikeCount() - 1));
            replyRepo.save(reply);
            return reply.getLikeCount();
        }
        throw new BusinessException(ErrorCode.NOT_FOUND, "目标内容不存在");
    }

    private int getTargetCount(String targetType, String targetId) {
        if ("post".equals(targetType)) {
            return postRepo.findById(targetId).map(Post::getLikeCount).orElse(0);
        }
        if ("reply".equals(targetType)) {
            return replyRepo.findById(targetId).map(Reply::getLikeCount).orElse(0);
        }
        return 0;
    }

    private String getTargetAuthorId(String targetType, String targetId) {
        if ("post".equals(targetType)) {
            return postRepo.findById(targetId).map(Post::getAuthorId).orElse(null);
        }
        if ("reply".equals(targetType)) {
            return replyRepo.findById(targetId).map(Reply::getAuthorId).orElse(null);
        }
        return null;
    }
}
