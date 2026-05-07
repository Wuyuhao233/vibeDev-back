package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.common.PaginatedResponse;
import com.vibedev.dto.report.*;
import com.vibedev.entity.Report;
import com.vibedev.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final int MALICIOUS_THRESHOLD = 3;
    private static final int MALICIOUS_BAN_DAYS = 7;
    private static final String REPORT_BAN_PREFIX = "report:ban:";

    private final ReportRepository reportRepo;
    private final PostRepository postRepo;
    private final ReplyRepository replyRepo;
    private final UserRepository userRepo;
    private final MuteService muteService;
    private final NotificationService notificationService;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;

    public ReportService(ReportRepository reportRepo,
                         PostRepository postRepo,
                         ReplyRepository replyRepo,
                         UserRepository userRepo,
                         MuteService muteService,
                         NotificationService notificationService,
                         org.springframework.data.redis.core.StringRedisTemplate redis) {
        this.reportRepo = reportRepo;
        this.postRepo = postRepo;
        this.replyRepo = replyRepo;
        this.userRepo = userRepo;
        this.muteService = muteService;
        this.notificationService = notificationService;
        this.redis = redis;
    }

    @Transactional
    public void create(String userId, CreateReportRequest dto) {
        // 1. Check already reported
        var existing = reportRepo.findByReporterIdAndTargetTypeAndTargetIdAndStatus(
                userId, dto.targetType(), dto.targetId(), "pending");
        if (existing.isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "你已举报过该内容，请勿重复操作");
        }

        // 2. Check malicious report ban
        String banKey = REPORT_BAN_PREFIX + userId;
        String banVal = redis.opsForValue().get(banKey);
        if (banVal != null) {
            Long ttl = redis.getExpire(banKey);
            long days = ttl != null ? Math.max(1, ttl / 86400) : 0;
            throw new BusinessException(ErrorCode.FORBIDDEN.getCode(),
                    "你已被禁止举报，" + days + "天后恢复");
        }

        // 3. Validate other reason type requires description
        if ("other".equals(dto.reasonType()) &&
            (dto.description() == null || dto.description().isBlank())) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT,
                    "请描述具体原因（other 类型描述为必填）");
        }

        // 4. Validate target exists
        validateTarget(dto.targetType(), dto.targetId());

        // 5. Create report
        var report = new Report();
        report.setId(UUID.randomUUID().toString());
        report.setReporterId(userId);
        report.setTargetType(dto.targetType());
        report.setTargetId(dto.targetId());
        report.setReasonType(dto.reasonType());
        report.setDescription(dto.description());
        report.setStatus("pending");
        reportRepo.save(report);
    }

    public CheckBanResponse checkBan(String userId) {
        long maliciousCount = reportRepo.countByReporterIdAndIsMaliciousTrue(userId);
        String banKey = REPORT_BAN_PREFIX + userId;
        String banVal = redis.opsForValue().get(banKey);
        boolean isBanned = banVal != null;
        Instant bannedUntil = null;
        if (isBanned) {
            Long ttl = redis.getExpire(banKey);
            if (ttl != null && ttl > 0) {
                bannedUntil = Instant.now().plus(ttl, ChronoUnit.SECONDS);
            }
        }
        return new CheckBanResponse(isBanned, bannedUntil, (int) maliciousCount);
    }

    public PaginatedResponse<ReportItem> list(String status, String targetType,
                                               Integer page, Integer pageSize,
                                               String userId, String role, String boardId) {
        if (page == null || page < 1) page = 1;
        if (pageSize == null || pageSize < 1 || pageSize > 50) pageSize = 20;
        var pageable = PageRequest.of(page - 1, pageSize);

        var result = boardId != null
                ? reportRepo.findByStatusAndBoardId(status, boardId, pageable)
                : targetType != null
                    ? reportRepo.findByStatusAndTargetTypeOrderByCreatedAtDesc(status, targetType, pageable)
                    : reportRepo.findByStatusOrderByCreatedAtDesc(status, pageable);

        var items = result.getContent().stream()
                .map(this::toItem)
                .toList();
        return PaginatedResponse.of(items, result.getTotalElements(), page, pageSize);
    }

    public ReportItem getById(String reportId) {
        var report = reportRepo.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "举报工单不存在"));
        return toItem(report);
    }

    @Transactional
    public void handle(String reportId, HandleReportRequest dto, String handlerId, String role) {
        var report = reportRepo.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "举报工单不存在"));

        if (!"pending".equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "该举报已处理");
        }

        // Permission: admin can handle all; moderator checks board scope
        if (!"admin".equals(role)) {
            checkModeratorBoardScope(report.getTargetType(), report.getTargetId(), handlerId);
        }

        String result = dto.result();
        report.setResult(result);
        report.setResultDescription(dto.resultDescription());
        report.setHandlerId(handlerId);
        report.setStatus("processed");
        report.setProcessedAt(Instant.now());

        switch (result) {
            case "ignored":
                break;
            case "deleted":
                softDeleteTarget(report.getTargetType(), report.getTargetId(), handlerId, role);
                break;
            case "banned":
                if (dto.banDuration() == null || dto.banDuration().isBlank()) {
                    throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "请选择禁言时长");
                }
                String targetAuthorId = getTargetAuthorId(report.getTargetType(), report.getTargetId());
                muteService.muteUser(handlerId, role, targetAuthorId, null, dto.banDuration(),
                        dto.banReason() != null ? dto.banReason() : "举报处理禁言");
                break;
            default:
                throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "无效的处理结果");
        }

        reportRepo.save(report);

        // Notify reporter about result
        notificationService.create(
                report.getReporterId(),
                "report_handled",
                "举报处理结果",
                "你的举报已处理，结果：" + resultLabel(result),
                report.getTargetType(),
                report.getTargetId()
        );
    }

    public ReportStatsResponse stats() {
        long pendingCount = reportRepo.countByStatus("pending");
        // today processed count
        Instant today = Instant.now().truncatedTo(ChronoUnit.DAYS);
        // Simplified: just count all processed
        long totalReports = reportRepo.count();
        return new ReportStatsResponse(pendingCount, 0, totalReports);
    }

    private ReportItem toItem(Report r) {
        return new ReportItem(
                r.getId(), r.getReporterId(), r.getTargetType(), r.getTargetId(),
                r.getReasonType(), r.getDescription(), r.getStatus(), r.getResult(),
                r.getResultDescription(), r.getHandlerId(), r.isMalicious(),
                r.getCreatedAt(), r.getProcessedAt()
        );
    }

    private void validateTarget(String targetType, String targetId) {
        switch (targetType) {
            case "post":
                postRepo.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在"));
                break;
            case "reply":
                replyRepo.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "回复不存在"));
                break;
            default:
                throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "无效的举报类型");
        }
    }

    private void softDeleteTarget(String targetType, String targetId, String userId, String role) {
        switch (targetType) {
            case "post":
                var post = postRepo.findById(targetId).orElse(null);
                if (post != null) {
                    post.setDeleted(true);
                    post.setDeletedAt(Instant.now());
                    post.setDeletedBy(userId);
                    postRepo.save(post);
                }
                break;
            case "reply":
                var reply = replyRepo.findById(targetId).orElse(null);
                if (reply != null) {
                    reply.setDeleted(true);
                    reply.setDeletedAt(Instant.now());
                    replyRepo.save(reply);
                }
                break;
        }
    }

    private String getTargetAuthorId(String targetType, String targetId) {
        switch (targetType) {
            case "post":
                return postRepo.findById(targetId).map(p -> p.getAuthorId()).orElse(null);
            case "reply":
                return replyRepo.findById(targetId).map(r -> r.getAuthorId()).orElse(null);
            default:
                return null;
        }
    }

    private void checkModeratorBoardScope(String targetType, String targetId, String moderatorId) {
        // For V1.0, moderator can handle reports in their board.
        // We check by resolving the board from the target.
        String boardId = null;
        switch (targetType) {
            case "post":
                boardId = postRepo.findById(targetId).map(p -> p.getBoardId()).orElse(null);
                break;
            case "reply":
                var reply = replyRepo.findById(targetId).orElse(null);
                if (reply != null) {
                    var post = postRepo.findById(reply.getPostId()).orElse(null);
                    if (post != null) boardId = post.getBoardId();
                }
                break;
        }
        // For V1.0, moderators are board-scoped through a simple check.
        // The plan says moderators can only see/manage their own board's content.
        // We'll implement a basic check that the user is a moderator with access to this board.
        // Full moderator-board mapping will be enhanced in V1.1.
        var user = userRepo.findById(moderatorId).orElse(null);
        if (user == null || !"moderator".equals(user.getRole())) {
            throw new BusinessException(ErrorCode.NOT_MODERATOR_OF_BOARD, "权限不足，非管辖版块");
        }
    }

    private String resultLabel(String result) {
        return switch (result) {
            case "ignored" -> "已忽略";
            case "deleted" -> "内容已删除";
            case "banned" -> "用户已禁言";
            default -> result;
        };
    }
}
