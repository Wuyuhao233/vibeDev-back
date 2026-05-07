package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.common.PaginatedResponse;
import com.vibedev.dto.appeal.AppealItem;
import com.vibedev.dto.appeal.CreateAppealRequest;
import com.vibedev.dto.appeal.HandleAppealRequest;
import com.vibedev.dto.report.*;
import com.vibedev.entity.Appeal;
import com.vibedev.entity.ModerationQueue;
import com.vibedev.entity.Post;
import com.vibedev.entity.Reply;
import com.vibedev.entity.Report;
import com.vibedev.entity.User;
import com.vibedev.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

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
    private final AppealRepository appealRepo;
    private final ModerationQueueRepository queueRepo;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;

    public ReportService(ReportRepository reportRepo,
                         PostRepository postRepo,
                         ReplyRepository replyRepo,
                         UserRepository userRepo,
                         MuteService muteService,
                         NotificationService notificationService,
                         AppealRepository appealRepo,
                         ModerationQueueRepository queueRepo,
                         org.springframework.data.redis.core.StringRedisTemplate redis) {
        this.reportRepo = reportRepo;
        this.postRepo = postRepo;
        this.replyRepo = replyRepo;
        this.userRepo = userRepo;
        this.muteService = muteService;
        this.notificationService = notificationService;
        this.appealRepo = appealRepo;
        this.queueRepo = queueRepo;
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

    // ─── Appeal ───────────────────────────────────────────

    @Transactional
    public void submitAppeal(String reportId, String userId, CreateAppealRequest dto) {
        var report = reportRepo.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "举报工单不存在"));

        if (!"processed".equals(report.getStatus())) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "该举报尚未处理，无法申诉");
        }

        String result = report.getResult();
        if (!"deleted".equals(result) && !"banned".equals(result)) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "该处理结果不支持申诉");
        }

        // Verify appellant is the target content author
        String targetAuthorId = getTargetAuthorId(report.getTargetType(), report.getTargetId());
        if (targetAuthorId == null || !targetAuthorId.equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "权限不足，仅作者本人可申诉");
        }

        // Check duplicate appeal
        var existingAppeal = appealRepo.findByReportIdAndStatus(reportId, "pending");
        if (existingAppeal.isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "该举报已有待处理的申诉");
        }

        // Create appeal
        var appeal = new Appeal();
        appeal.setId(UUID.randomUUID().toString());
        appeal.setReportId(reportId);
        appeal.setAppellantId(userId);
        appeal.setReason(dto.reason());
        appeal.setStatus("pending");
        appealRepo.save(appeal);

        // Add to moderation queue with priority=1
        var q = new ModerationQueue();
        q.setId(UUID.randomUUID().toString());
        q.setTargetType(report.getTargetType());
        q.setTargetId(report.getTargetId());
        q.setAuthorId(targetAuthorId);
        q.setBoardId(getTargetBoardId(report.getTargetType(), report.getTargetId()));
        q.setPriority(1);
        q.setStatus("pending");
        q.setReason("申诉复审: " + dto.reason());
        queueRepo.save(q);

        // Notify admins about new appeal
        notificationService.create(userId, "appeal_submitted",
                "申诉已提交",
                "你的申诉已提交，将优先处理",
                report.getTargetType(), report.getTargetId());
    }

    public PaginatedResponse<AppealItem> listAppeals(String status, int page, int pageSize) {
        if (page < 1) page = 1;
        if (pageSize < 1 || pageSize > 50) pageSize = 20;
        var pageable = PageRequest.of(page - 1, pageSize);

        var result = appealRepo.findByStatusOrderByCreatedAtAsc(status, pageable);
        var appeals = result.getContent();

        if (appeals.isEmpty()) {
            return PaginatedResponse.of(List.of(), result.getTotalElements(), page, pageSize);
        }

        // Load related Reports
        List<String> reportIds = appeals.stream()
                .map(Appeal::getReportId).distinct().toList();
        Map<String, Report> reportsById = reportRepo.findAllById(reportIds).stream()
                .collect(Collectors.toMap(Report::getId, r -> r));

        // Collect targetType/targetId pairs for content and moderation data
        record TargetKey(String targetType, String targetId) {}
        List<TargetKey> targetKeys = reportsById.values().stream()
                .map(r -> new TargetKey(r.getTargetType(), r.getTargetId()))
                .distinct().toList();

        // Load Post content
        List<String> postIds = targetKeys.stream()
                .filter(t -> "post".equals(t.targetType()))
                .map(TargetKey::targetId).distinct().toList();
        Map<String, Post> postsById = postIds.isEmpty() ? Map.of()
                : postRepo.findAllById(postIds).stream()
                        .collect(Collectors.toMap(Post::getId, p -> p));

        // Load Reply content
        List<String> replyIds = targetKeys.stream()
                .filter(t -> "reply".equals(t.targetType()))
                .map(TargetKey::targetId).distinct().toList();
        Map<String, Reply> repliesById = replyIds.isEmpty() ? Map.of()
                : replyRepo.findAllById(replyIds).stream()
                        .collect(Collectors.toMap(Reply::getId, r -> r));

        // Load ModerationQueue entries for AI scores
        Map<String, Integer> aiScoresByTarget = new HashMap<>();
        Map<String, String> aiCategoriesByTarget = new HashMap<>();
        for (TargetKey tk : targetKeys) {
            var mq = queueRepo.findFirstByTargetTypeAndTargetIdAndAiScoreGreaterThanOrderByAiScoreDesc(
                    tk.targetType(), tk.targetId(), 0);
            mq.ifPresent(q -> {
                aiScoresByTarget.put(tk.targetType() + ":" + tk.targetId(), q.getAiScore());
                aiCategoriesByTarget.put(tk.targetType() + ":" + tk.targetId(), q.getAiCategory());
            });
        }

        // Load users: appellants + reviewers
        List<String> appellantIds = appeals.stream()
                .map(Appeal::getAppellantId).distinct().toList();
        List<String> handlerIds = appeals.stream()
                .map(Appeal::getHandlerId).filter(Objects::nonNull).distinct().toList();
        Set<String> allUserIds = new HashSet<>(appellantIds);
        allUserIds.addAll(handlerIds);
        Map<String, User> usersById = userRepo.findAllById(allUserIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        var items = appeals.stream().map(appeal -> {
            var report = reportsById.get(appeal.getReportId());
            String contentId = report != null ? report.getTargetId() : "";
            String targetType = report != null ? report.getTargetType() : "";
            String contentTitle = "";
            String contentSummary = "";
            String boardId = null;

            if ("post".equals(targetType)) {
                var post = postsById.get(contentId);
                if (post != null) {
                    contentTitle = post.getTitle();
                    contentSummary = truncateContent(post.getContentMarkdown(), 200);
                    boardId = post.getBoardId();
                }
            } else if ("reply".equals(targetType)) {
                var reply = repliesById.get(contentId);
                if (reply != null) {
                    contentTitle = "回复内容";
                    contentSummary = truncateContent(reply.getContentMarkdown(), 200);
                }
            }

            var appellant = usersById.get(appeal.getAppellantId());
            String appellantUsername = appellant != null ? appellant.getUsername() : "未知用户";

            var handler = usersById.get(appeal.getHandlerId());
            String reviewedBy = handler != null ? handler.getUsername() : null;

            String scoreKey = targetType + ":" + contentId;
            Integer aiScore = aiScoresByTarget.get(scoreKey);

            String violationCategory = aiCategoriesByTarget.get(scoreKey);
            if (violationCategory == null && report != null) {
                violationCategory = report.getReasonType();
            }

            return new AppealItem(
                    appeal.getId(),
                    contentId,
                    contentTitle,
                    contentSummary,
                    appellantUsername,
                    violationCategory,
                    aiScore,
                    appeal.getReason(),
                    appeal.getStatus(),
                    reviewedBy,
                    appeal.getHandlerNote(),
                    appeal.getCreatedAt(),
                    appeal.getProcessedAt()
            );
        }).toList();

        return PaginatedResponse.of(items, result.getTotalElements(), page, pageSize);
    }

    private String truncateContent(String content, int maxLength) {
        if (content == null) return "";
        if (content.length() <= maxLength) return content;
        return content.substring(0, maxLength) + "...";
    }

    @Transactional
    public void approveAppeal(String appealId, String handlerId) {
        var appeal = appealRepo.findById(appealId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "申诉不存在"));

        if (!"pending".equals(appeal.getStatus())) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "该申诉已被处理");
        }

        var report = reportRepo.findById(appeal.getReportId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "关联举报工单不存在"));

        // Undo the report action
        String reportResult = report.getResult();
        if ("deleted".equals(reportResult)) {
            restoreTarget(report.getTargetType(), report.getTargetId());
        } else if ("banned".equals(reportResult)) {
            String targetAuthorId = getTargetAuthorId(report.getTargetType(), report.getTargetId());
            if (targetAuthorId != null) {
                muteService.unmuteUser(handlerId, targetAuthorId);
            }
        }

        // Mark appeal as approved
        appeal.setStatus("approved");
        appeal.setHandlerId(handlerId);
        appeal.setProcessedAt(Instant.now());
        appealRepo.save(appeal);

        // Resolve the associated queue entry
        resolveAppealQueueEntry(report.getTargetType(), report.getTargetId());

        // Notify appellant
        notificationService.create(appeal.getAppellantId(), "appeal_approved",
                "申诉通过",
                "你的申诉已通过，内容已恢复",
                report.getTargetType(), report.getTargetId());
    }

    @Transactional
    public void rejectAppeal(String appealId, String handlerId, String note) {
        var appeal = appealRepo.findById(appealId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "申诉不存在"));

        if (!"pending".equals(appeal.getStatus())) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "该申诉已被处理");
        }

        appeal.setStatus("rejected");
        appeal.setHandlerId(handlerId);
        appeal.setHandlerNote(note);
        appeal.setProcessedAt(Instant.now());
        appealRepo.save(appeal);

        // Resolve the associated queue entry
        var report = reportRepo.findById(appeal.getReportId()).orElse(null);
        if (report != null) {
            resolveAppealQueueEntry(report.getTargetType(), report.getTargetId());
        }

        // Notify appellant
        notificationService.create(appeal.getAppellantId(), "appeal_rejected",
                "申诉驳回",
                "你的申诉已被驳回" + (note != null && !note.isBlank() ? "，原因：" + note : ""),
                report != null ? report.getTargetType() : null,
                report != null ? report.getTargetId() : null);
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

    private void restoreTarget(String targetType, String targetId) {
        switch (targetType) {
            case "post":
                postRepo.findById(targetId).ifPresent(p -> {
                    p.setDeleted(false);
                    p.setDeletedAt(null);
                    p.setDeletedBy(null);
                    p.setAuditStatus("approved");
                    postRepo.save(p);
                });
                break;
            case "reply":
                replyRepo.findById(targetId).ifPresent(r -> {
                    r.setDeleted(false);
                    r.setDeletedAt(null);
                    r.setAuditStatus("approved");
                    replyRepo.save(r);
                });
                break;
        }
    }

    private String getTargetBoardId(String targetType, String targetId) {
        switch (targetType) {
            case "post":
                return postRepo.findById(targetId).map(p -> p.getBoardId()).orElse(null);
            case "reply":
                var reply = replyRepo.findById(targetId).orElse(null);
                if (reply != null) {
                    return postRepo.findById(reply.getPostId()).map(p -> p.getBoardId()).orElse(null);
                }
                return null;
            default:
                return null;
        }
    }

    private void resolveAppealQueueEntry(String targetType, String targetId) {
        var queueEntry = queueRepo.findByTargetTypeAndTargetIdAndStatus(targetType, targetId, "pending");
        queueEntry.ifPresent(q -> {
            q.setStatus("approved");
            q.setHandledAt(Instant.now());
            queueRepo.save(q);
        });
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
