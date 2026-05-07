package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.dto.mute.MuteRecordItem;
import com.vibedev.dto.mute.MuteRequest;
import com.vibedev.entity.MuteRecord;
import com.vibedev.repository.MuteRecordRepository;
import com.vibedev.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
public class MuteService {

    private static final Logger log = LoggerFactory.getLogger(MuteService.class);
    private static final Instant PERMANENT = Instant.parse("2099-01-01T00:00:00Z");

    private final MuteRecordRepository muteRecordRepo;
    private final UserRepository userRepo;
    private final NotificationService notificationService;
    private final PointsService pointsService;

    public MuteService(MuteRecordRepository muteRecordRepo,
                       UserRepository userRepo,
                       NotificationService notificationService,
                       PointsService pointsService) {
        this.muteRecordRepo = muteRecordRepo;
        this.userRepo = userRepo;
        this.notificationService = notificationService;
        this.pointsService = pointsService;
    }

    @Transactional
    public void muteUser(String moderatorId, String moderatorRole, String targetUserId,
                         String boardId, String duration, String reason) {
        // 1. Validate target exists
        var target = userRepo.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        // 2. Admin cannot be muted
        if ("admin".equals(target.getRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "不能禁言管理员");
        }

        // 3. Already banned check
        if (target.isBanned() && target.getBannedUntil() != null &&
            target.getBannedUntil().isAfter(Instant.now())) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "该用户已被禁言");
        }

        // 4. Calculate banned_until
        Instant bannedUntil = calculateBannedUntil(duration);
        target.setBanned(true);
        target.setBannedUntil(bannedUntil);
        userRepo.save(target);

        // 5. Create mute record
        var record = new MuteRecord();
        record.setId(UUID.randomUUID().toString());
        record.setUserId(targetUserId);
        record.setModeratorId(moderatorId);
        record.setBoardId(boardId);
        record.setDuration(duration);
        record.setReason(reason);
        muteRecordRepo.save(record);

        // 6. Deduct points (-20)
        pointsService.deductPoints(targetUserId, 20, "muted_penalty", null);

        // 7. Send notification
        notificationService.create(
                targetUserId,
                "user_banned",
                "你已被禁言",
                "禁言原因：" + reason + "，时长：" + durationLabel(duration),
                null,
                null
        );
    }

    @Transactional
    public void unmuteUser(String adminId, String targetUserId) {
        var target = userRepo.findById(targetUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        if (!target.isBanned()) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "该用户未被禁言");
        }

        target.setBanned(false);
        target.setBannedUntil(null);
        userRepo.save(target);

        // Update active mute records
        var activeRecord = muteRecordRepo.findByUserIdAndIsActiveTrue(targetUserId);
        activeRecord.ifPresent(r -> {
            r.setActive(false);
            r.setUnmutedBy(adminId);
            r.setUnmutedAt(Instant.now());
            muteRecordRepo.save(r);
        });
    }

    public List<MuteRecordItem> getHistory(String targetUserId) {
        return muteRecordRepo.findByUserIdOrderByCreatedAtDesc(targetUserId).stream()
                .map(r -> new MuteRecordItem(
                        r.getId(), r.getUserId(), r.getModeratorId(),
                        r.getDuration(), r.getReason(), r.isActive(),
                        r.getUnmutedBy(), r.getUnmutedAt(), r.getCreatedAt()
                ))
                .toList();
    }

    public boolean isUserBanned(String userId) {
        var user = userRepo.findById(userId).orElse(null);
        if (user == null) return false;
        if (!user.isBanned()) return false;
        Instant bannedUntil = user.getBannedUntil();
        if (bannedUntil == null) return false;
        if (bannedUntil.isBefore(Instant.now())) {
            // Ban expired, auto-release
            user.setBanned(false);
            user.setBannedUntil(null);
            userRepo.save(user);
            return false;
        }
        return true;
    }

    private Instant calculateBannedUntil(String duration) {
        return switch (duration) {
            case "1h" -> Instant.now().plus(1, ChronoUnit.HOURS);
            case "1d" -> Instant.now().plus(1, ChronoUnit.DAYS);
            case "3d" -> Instant.now().plus(3, ChronoUnit.DAYS);
            case "7d" -> Instant.now().plus(7, ChronoUnit.DAYS);
            case "permanent" -> PERMANENT;
            default -> throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "无效的禁言时长");
        };
    }

    private String durationLabel(String duration) {
        return switch (duration) {
            case "1h" -> "1小时";
            case "1d" -> "1天";
            case "3d" -> "3天";
            case "7d" -> "7天";
            case "permanent" -> "永久";
            default -> duration;
        };
    }
}
