package com.vibedev.service;

import com.vibedev.client.AiModerationClient;
import com.vibedev.client.AiModerationClient.AuditResult;
import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.dto.moderation.*;
import com.vibedev.dto.user.UserSummary;
import com.vibedev.entity.*;
import com.vibedev.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ModerationService {

    private static final Logger log = LoggerFactory.getLogger(ModerationService.class);
    private static final BigDecimal MONTHLY_BUDGET_DEFAULT = BigDecimal.valueOf(200);

    private final ModerationQueueRepository queueRepo;
    private final ModerationLogRepository logRepo;
    private final AiAuditLogRepository aiAuditLogRepo;
    private final UserRepository userRepo;
    private final BoardRepository boardRepo;
    private final PostRepository postRepo;
    private final ReplyRepository replyRepo;
    private final ReportRepository reportRepo;
    private final NotificationService notificationService;
    private final AiModerationClient aiModerationClient;

    @Value("${app.moderation.monthly-budget:200}")
    private BigDecimal monthlyBudget;

    public ModerationService(ModerationQueueRepository queueRepo,
                            ModerationLogRepository logRepo,
                            AiAuditLogRepository aiAuditLogRepo,
                            UserRepository userRepo,
                            BoardRepository boardRepo,
                            PostRepository postRepo,
                            ReplyRepository replyRepo,
                            ReportRepository reportRepo,
                            NotificationService notificationService,
                            AiModerationClient aiModerationClient) {
        this.queueRepo = queueRepo;
        this.logRepo = logRepo;
        this.aiAuditLogRepo = aiAuditLogRepo;
        this.userRepo = userRepo;
        this.boardRepo = boardRepo;
        this.postRepo = postRepo;
        this.replyRepo = replyRepo;
        this.reportRepo = reportRepo;
        this.notificationService = notificationService;
        this.aiModerationClient = aiModerationClient;
    }

    // ─── AI Review (async) ────────────────────────────────

    @Async("aiAuditExecutor")
    public void reviewAsync(String targetType, String targetId, String content,
                           String userId, String title, String boardId) {
        // 1. Check monthly budget
        if (isBudgetExceeded()) {
            enqueueForManualReview(targetType, targetId, content, userId, title, boardId,
                    -1, "unknown", true, 0);
            log.info("Monthly budget exceeded, degraded to manual review for {}:{}", targetType, targetId);
            return;
        }

        // 2. Call AI moderation
        long start = System.currentTimeMillis();
        AuditResult result = aiModerationClient.audit(content);
        long elapsed = System.currentTimeMillis() - start;

        // 3. Log AI call
        var aiLog = new AiAuditLog();
        aiLog.setId(UUID.randomUUID().toString());
        aiLog.setTargetType(targetType);
        aiLog.setTargetId(targetId);
        aiLog.setRequestChars(content != null ? content.length() : 0);
        aiLog.setResponseScore(result.score());
        aiLog.setResponseCategory(result.category());
        aiLog.setDegraded(result.degraded());
        aiLog.setResponseTimeMs((int) elapsed);
        // Mock cost: ~0.001 CNY per 1000 chars
        aiLog.setCost(BigDecimal.valueOf(Math.max(0.0001, (content != null ? content.length() : 0) * 0.000001)));
        aiAuditLogRepo.save(aiLog);

        // 4. Process result
        if (result.degraded()) {
            enqueueForManualReview(targetType, targetId, content, userId, title, boardId,
                    -1, "unknown", true, 0);
        } else {
            processAiResult(targetType, targetId, content, userId, title, boardId, result);
        }
    }

    private void processAiResult(String targetType, String targetId, String content,
                                 String userId, String title, String boardId, AuditResult result) {
        int score = result.score();

        if (score < 50) {
            // Auto-pass
            updateContentStatus(targetType, targetId, "approved");
            createAuditLog(targetType, targetId, "ai_approved", null, null);
            log.debug("AI auto-approved {}:{} (score={})", targetType, targetId, score);
        } else if (score >= 85) {
            // Auto-reject
            updateContentStatus(targetType, targetId, "rejected");
            createAuditLog(targetType, targetId, "ai_rejected", null,
                    "AI自动拦截: " + result.category() + " - " + result.reason());
            enqueueRecord(targetType, targetId, content, userId, title, boardId,
                    score, result.category(), false, "rejected", 0);
            notificationService.create(userId, "content_rejected",
                    "内容未通过审核",
                    "你的内容因「" + result.category() + "」被拦截：" + result.reason(),
                    targetType, targetId);
            log.debug("AI auto-rejected {}:{} (score={}, category={})",
                    targetType, targetId, score, result.category());
        } else {
            // Needs manual review
            enqueueForManualReview(targetType, targetId, content, userId, title, boardId,
                    score, result.category(), false, 0);
            updateContentStatus(targetType, targetId, "pending_review");
            log.debug("Content {}:{} queued for manual review (score={}, category={})",
                    targetType, targetId, score, result.category());
        }
    }

    // ─── Manual review operations ─────────────────────────

    @Transactional
    public void manualApprove(String queueId, String moderatorId, String moderatorRole) {
        var queueItem = queueRepo.findById(queueId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "审核条目不存在"));

        if (!"pending".equals(queueItem.getStatus())) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "该条目已被处理");
        }

        // Check moderator's board permission
        if ("moderator".equals(moderatorRole) && queueItem.getBoardId() != null) {
            checkBoardModerator(moderatorId, queueItem.getBoardId());
        }

        queueItem.setStatus("approved");
        queueItem.setModeratorId(moderatorId);
        queueItem.setHandledAt(Instant.now());
        queueRepo.save(queueItem);

        updateContentStatus(queueItem.getTargetType(), queueItem.getTargetId(), "approved");
        createAuditLog(queueItem.getTargetType(), queueItem.getTargetId(),
                "manual_approved", moderatorId, null);

        log.info("Moderator {} approved {}:{}", moderatorId,
                queueItem.getTargetType(), queueItem.getTargetId());
    }

    @Transactional
    public void manualReject(String queueId, String moderatorId, String moderatorRole, String reason) {
        var queueItem = queueRepo.findById(queueId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "审核条目不存在"));

        if (!"pending".equals(queueItem.getStatus())) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "该条目已被处理");
        }

        if ("moderator".equals(moderatorRole) && queueItem.getBoardId() != null) {
            checkBoardModerator(moderatorId, queueItem.getBoardId());
        }

        queueItem.setStatus("rejected");
        queueItem.setModeratorId(moderatorId);
        queueItem.setReason(reason);
        queueItem.setHandledAt(Instant.now());
        queueRepo.save(queueItem);

        updateContentStatus(queueItem.getTargetType(), queueItem.getTargetId(), "rejected");
        createAuditLog(queueItem.getTargetType(), queueItem.getTargetId(),
                "manual_rejected", moderatorId, reason);

        // Notify author
        notificationService.create(queueItem.getAuthorId(), "content_rejected",
                "内容未通过审核",
                "你的内容被驳回，原因：" + reason,
                queueItem.getTargetType(), queueItem.getTargetId());

        log.info("Moderator {} rejected {}:{} reason={}", moderatorId,
                queueItem.getTargetType(), queueItem.getTargetId(), reason);
    }

    // ─── Review queue listing ─────────────────────────────

    public ReviewQueueListResponse getReviewQueue(ReviewQueueFilter filter,
                                                   String userId, String role) {
        var pageable = PageRequest.of(filter.page() - 1, filter.pageSize());

        List<String> moderatorBoardIds = null;
        if ("moderator".equals(role)) {
            // Moderators see all boards for now (board-moderator mapping to be added later)
            moderatorBoardIds = boardRepo.findByStatusAndIsDeletedFalseOrderBySortOrder("active")
                    .stream().map(Board::getId).toList();
            if (moderatorBoardIds.isEmpty()) {
                return new ReviewQueueListResponse(List.of(),
                        new ReviewQueueStats(0, 0, 0), 0, filter.page(), filter.pageSize());
            }
        }

        var page = getQueuePage(filter, moderatorBoardIds, pageable);

        // Load authors and board names
        var authorIds = page.getContent().stream()
                .map(ModerationQueue::getAuthorId).distinct().toList();
        var usersById = authorIds.isEmpty() ? Map.<String, User>of()
                : userRepo.findAllById(authorIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u));

        var boardIds = page.getContent().stream()
                .map(ModerationQueue::getBoardId).filter(Objects::nonNull).distinct().toList();
        var boardsById = boardIds.isEmpty() ? Map.<String, Board>of()
                : boardRepo.findAllById(boardIds).stream()
                        .collect(Collectors.toMap(Board::getId, b -> b));

        var items = page.getContent().stream()
                .map(mq -> {
                    var user = usersById.get(mq.getAuthorId());
                    var author = user != null ? UserSummary.from(user) :
                            new UserSummary("unknown", "unknown", "未知用户", "", 1, "user");
                    var board = boardsById.get(mq.getBoardId());
                    String boardName = board != null ? board.getName() : "";
                    return ReviewQueueItem.from(mq, author, boardName);
                })
                .toList();

        // Stats
        long pendingCount = queueRepo.countByStatus("pending");
        Instant todayStart = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        long todayApproved = queueRepo.countApprovedSince(todayStart);
        long todayRejected = queueRepo.countRejectedSince(todayStart);

        return new ReviewQueueListResponse(items,
                new ReviewQueueStats(pendingCount, todayApproved, todayRejected),
                page.getTotalElements(), filter.page(), filter.pageSize());
    }

    private org.springframework.data.domain.Page<ModerationQueue> getQueuePage(
            ReviewQueueFilter filter, List<String> boardIds, PageRequest pageable) {
        boolean hasBoardFilter = boardIds != null && !boardIds.isEmpty();
        boolean hasTypeFilter = filter.targetType() != null && !filter.targetType().isBlank();

        if (hasBoardFilter && hasTypeFilter) {
            return queueRepo.findByStatusAndTargetTypeAndBoardIdInOrderByPriorityDescCreatedAtAsc(
                    filter.status(), filter.targetType(), boardIds, pageable);
        } else if (hasBoardFilter) {
            return queueRepo.findByStatusAndBoardIdInOrderByPriorityDescCreatedAtAsc(
                    filter.status(), boardIds, pageable);
        } else if (hasTypeFilter) {
            return queueRepo.findByStatusAndTargetTypeOrderByPriorityDescCreatedAtAsc(
                    filter.status(), filter.targetType(), pageable);
        } else {
            return queueRepo.findByStatusOrderByPriorityDescCreatedAtAsc(
                    filter.status(), pageable);
        }
    }

    // ─── Review stats ─────────────────────────────────────

    public ReviewStatsResponse getReviewStats(String userId, String role) {
        Instant todayStart = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        Instant weekStart = Instant.now().minus(7, java.time.temporal.ChronoUnit.DAYS).truncatedTo(java.time.temporal.ChronoUnit.DAYS);
        Instant monthStart = YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();

        // Queue stats
        long pendingCount = queueRepo.countByStatus("pending");
        long todayApproved = queueRepo.countApprovedSince(todayStart);
        long todayRejected = queueRepo.countRejectedSince(todayStart);

        // Report stats
        long reportPending = reportRepo.countByStatus("pending");
        long reportsResolvedToday = reportRepo.countByStatusAndProcessedAtAfter("processed", todayStart);

        // Quality stats
        long totalMonth = Math.max(1, queueRepo.countApprovedSince(monthStart)
                + queueRepo.countRejectedSince(monthStart));
        long autoApproved = queueRepo.countAutoApprovedSince(monthStart);
        long autoRejected = queueRepo.countAutoRejectedSince(monthStart);
        long manualApproved = logRepo.countByActionSince("manual_approved", monthStart);

        double passRate = (double) autoApproved / Math.max(1, totalMonth);
        double blockRate = (double) autoRejected / Math.max(1, totalMonth);
        double manualPassRate = (double) manualApproved
                / Math.max(1, autoApproved + manualApproved
                + logRepo.countByActionSince("manual_rejected", monthStart));
        double falsePositiveRate = 0.0; // Requires appeal data (V1.2)
        double missRate = 0.0; // Requires historical data

        // Cost stats
        BigDecimal monthCost = aiAuditLogRepo.sumCostSince(monthStart);
        long dailyCalls = aiAuditLogRepo.countByCreatedAtAfter(todayStart);
        boolean budgetExceeded = isBudgetExceeded();

        return new ReviewStatsResponse(
                new ReviewStatsResponse.QueueStats(pendingCount, 0, todayApproved, todayRejected),
                new ReviewStatsResponse.ReportStats(reportPending, reportsResolvedToday),
                new ReviewStatsResponse.QualityStats(passRate, blockRate, manualPassRate,
                        falsePositiveRate, missRate),
                new ReviewStatsResponse.CostStats(monthlyBudget != null ? monthlyBudget : MONTHLY_BUDGET_DEFAULT,
                        monthCost != null ? monthCost : BigDecimal.ZERO, dailyCalls, budgetExceeded)
        );
    }

    // ─── Quality audit (V1.2) ─────────────────────────────

    public QualityAuditSampleResponse sampleForQualityAudit(double sampleRate) {
        Instant monthStart = YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        var approvedPosts = postRepo.findApprovedSince(monthStart);
        long totalApproved = postRepo.countApprovedSince(monthStart);

        if (approvedPosts.isEmpty()) {
            return new QualityAuditSampleResponse(List.of(), 0, totalApproved, sampleRate);
        }

        int sampleSize = Math.max(1, (int) Math.ceil(approvedPosts.size() * sampleRate));
        var shuffled = new java.util.ArrayList<>(approvedPosts);
        java.util.Collections.shuffle(shuffled);
        var sampled = shuffled.subList(0, Math.min(sampleSize, shuffled.size()));

        var items = sampled.stream()
                .map(p -> new QualityAuditSampleResponse.SampleItem(
                        p.getId(), p.getTitle(), p.getAuthorId(), p.getBoardId(),
                        p.getCreatedAt(), p.getReplyCount(), p.getLikeCount()))
                .toList();

        return new QualityAuditSampleResponse(items, items.size(), totalApproved, sampleRate);
    }

    // ─── Private helpers ──────────────────────────────────

    private void enqueueForManualReview(String targetType, String targetId, String content,
                                        String userId, String title, String boardId,
                                        int score, String category, boolean degraded, int priority) {
        enqueueRecord(targetType, targetId, content, userId, title, boardId,
                score, category, degraded, "pending", priority);
        updateContentStatus(targetType, targetId, "pending_review");
    }

    private void enqueueRecord(String targetType, String targetId, String content,
                               String userId, String title, String boardId,
                               int score, String category, boolean degraded,
                               String status, int priority) {
        var q = new ModerationQueue();
        q.setId(UUID.randomUUID().toString());
        q.setTargetType(targetType);
        q.setTargetId(targetId);
        q.setContentSnapshot(content);
        q.setAuthorId(userId);
        q.setTargetTitle(title);
        q.setBoardId(boardId);
        q.setAiScore(score);
        q.setAiCategory(category);
        q.setAiDegraded(degraded);
        q.setPriority(priority);
        q.setStatus(status);
        queueRepo.save(q);
    }

    private void updateContentStatus(String targetType, String targetId, String status) {
        if ("post".equals(targetType)) {
            postRepo.findById(targetId).ifPresent(p -> {
                p.setAuditStatus(status);
                postRepo.save(p);
            });
        } else if ("reply".equals(targetType)) {
            replyRepo.findById(targetId).ifPresent(r -> {
                r.setAuditStatus(status);
                replyRepo.save(r);
            });
        }
    }

    private void createAuditLog(String targetType, String targetId,
                                String action, String operatorId, String reason) {
        var logEntry = new ModerationLog();
        logEntry.setId(UUID.randomUUID().toString());
        logEntry.setTargetType(targetType);
        logEntry.setTargetId(targetId);
        logEntry.setAction(action);
        logEntry.setOperatorId(operatorId);
        logEntry.setReason(reason);
        logRepo.save(logEntry);
    }

    private void checkBoardModerator(String moderatorId, String boardId) {
        boardRepo.findById(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "版块不存在"));
        // Board-moderator mapping not yet implemented; moderators can access all boards
    }

    private boolean isBudgetExceeded() {
        Instant monthStart = YearMonth.now(ZoneOffset.UTC).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        BigDecimal cost = aiAuditLogRepo.sumCostSince(monthStart);
        BigDecimal budget = monthlyBudget != null ? monthlyBudget : MONTHLY_BUDGET_DEFAULT;
        return cost != null && cost.compareTo(budget) >= 0;
    }
}
