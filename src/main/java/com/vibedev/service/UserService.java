package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.common.PaginatedResponse;
import com.vibedev.dto.user.*;
import com.vibedev.entity.*;
import com.vibedev.repository.*;
import com.vibedev.security.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepo;
    private final PostRepository postRepo;
    private final ReplyRepository replyRepo;
    private final FavoriteRepository favoriteRepo;
    private final BoardRepository boardRepo;
    private final BrowsingHistoryRepository browsingHistoryRepo;
    private final LoginHistoryRepository loginHistoryRepo;
    private final UserExportTaskRepository exportTaskRepo;
    private final FileStorageService fileStorageService;
    private final PasswordEncoder passwordEncoder;
    private final StringRedisTemplate redis;

    private final String baseUrl;

    public UserService(UserRepository userRepo, PostRepository postRepo,
                       ReplyRepository replyRepo, FavoriteRepository favoriteRepo,
                       BoardRepository boardRepo, BrowsingHistoryRepository browsingHistoryRepo,
                       LoginHistoryRepository loginHistoryRepo, UserExportTaskRepository exportTaskRepo,
                       FileStorageService fileStorageService, PasswordEncoder passwordEncoder,
                       StringRedisTemplate redis,
                       @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.userRepo = userRepo;
        this.postRepo = postRepo;
        this.replyRepo = replyRepo;
        this.favoriteRepo = favoriteRepo;
        this.boardRepo = boardRepo;
        this.browsingHistoryRepo = browsingHistoryRepo;
        this.loginHistoryRepo = loginHistoryRepo;
        this.exportTaskRepo = exportTaskRepo;
        this.fileStorageService = fileStorageService;
        this.passwordEncoder = passwordEncoder;
        this.redis = redis;
        this.baseUrl = baseUrl;
    }

    // ─── Profile ──────────────────────────────────────────

    public UserProfile getProfile(String username, String viewerId) {
        var user = userRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        String email = maskEmail(user.getEmail());
        // moderators/admins can see full email
        if (viewerId != null) {
            var viewer = userRepo.findById(viewerId).orElse(null);
            if (viewer != null && ("moderator".equals(viewer.getRole()) || "admin".equals(viewer.getRole()))) {
                email = user.getEmail();
            }
        }

        return new UserProfile(
                user.getId(), user.getUsername(), email,
                user.getNickname(), user.getSignature(), user.getAvatarUrl(),
                user.getRole(), user.getLevel(), user.getPoints(),
                user.isActivated(), user.isBanned(), user.getBannedUntil(),
                user.getCreatedAt(), user.getLastLoginAt());
    }

    // ─── User Posts ───────────────────────────────────────

    public PaginatedResponse<UserPostItem> getUserPosts(String username, int page, int pageSize) {
        var user = userRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        Pageable pageable = PageRequest.of(page - 1, pageSize);
        var postsPage = postRepo.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getId(), pageable);

        var items = postsPage.getContent().stream()
                .map(p -> new UserPostItem(
                        p.getId(), p.getTitle(),
                        boardRepo.findById(p.getBoardId()).map(Board::getName).orElse(""),
                        Collections.emptyList(), // tags populated by post module
                        p.getLikeCount(), p.getReplyCount(), p.getCollectCount(),
                        p.isEssence(), p.isDeleted(),
                        p.getCreatedAt(), p.getLastEditedAt()))
                .toList();

        return PaginatedResponse.of(items, postsPage.getTotalElements(), page, pageSize);
    }

    // ─── User Replies ─────────────────────────────────────

    public PaginatedResponse<UserReplyItem> getUserReplies(String username, int page, int pageSize) {
        var user = userRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        Pageable pageable = PageRequest.of(page - 1, pageSize);
        var repliesPage = replyRepo.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(user.getId(), pageable);

        var items = repliesPage.getContent().stream()
                .map(r -> {
                    String postTitle = postRepo.findById(r.getPostId())
                            .map(Post::getTitle).orElse("原帖已删除");
                    String summary = r.getContentMarkdown();
                    if (summary != null && summary.length() > 150) {
                        summary = summary.substring(0, 150);
                    }
                    return new UserReplyItem(
                            r.getId(), r.getPostId(), postTitle,
                            summary != null ? summary : "",
                            r.getLikeCount(), r.isDeleted(), r.getCreatedAt());
                })
                .toList();

        return PaginatedResponse.of(items, repliesPage.getTotalElements(), page, pageSize);
    }

    // ─── User Favorites ───────────────────────────────────

    public PaginatedResponse<UserCollectionItem> getUserFavorites(String username, String viewerId, int page, int pageSize) {
        var user = userRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        // only self can view favorites
        if (viewerId == null || !viewerId.equals(user.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能查看自己的收藏");
        }

        Pageable pageable = PageRequest.of(page - 1, pageSize);
        var favPage = favoriteRepo.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);

        var items = favPage.getContent().stream()
                .map(f -> {
                    var post = postRepo.findById(f.getPostId()).orElse(null);
                    boolean postDeleted = post == null || post.isDeleted();
                    String postTitle = postDeleted ? "原帖已删除" : post.getTitle();
                    String postAuthor = "";
                    String boardName = "";
                    if (post != null) {
                        postAuthor = userRepo.findById(post.getAuthorId()).map(User::getUsername).orElse("");
                        boardName = boardRepo.findById(post.getBoardId()).map(Board::getName).orElse("");
                    }
                    return new UserCollectionItem(
                            f.getId(), f.getPostId(), postTitle, postAuthor, boardName,
                            Collections.emptyList(), postDeleted, f.getCreatedAt());
                })
                .toList();

        return PaginatedResponse.of(items, favPage.getTotalElements(), page, pageSize);
    }

    // ─── Browsing History ─────────────────────────────────

    public PaginatedResponse<BrowseHistoryItem> getUserHistory(String username, String viewerId, int page, int pageSize) {
        var user = userRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在"));

        if (viewerId == null || !viewerId.equals(user.getId())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只能查看自己的浏览历史");
        }

        Pageable pageable = PageRequest.of(page - 1, pageSize);
        var historyPage = browsingHistoryRepo.findByUserIdOrderByViewedAtDesc(user.getId(), pageable);

        var items = historyPage.getContent().stream()
                .map(h -> {
                    String postTitle = postRepo.findById(h.getPostId())
                            .map(Post::getTitle).orElse("原帖已删除");
                    return new BrowseHistoryItem(h.getPostId(), postTitle, h.getViewedAt());
                })
                .toList();

        return PaginatedResponse.of(items, historyPage.getTotalElements(), page, pageSize);
    }

    // ─── Update Profile ───────────────────────────────────

    @Transactional
    public UserSummary updateProfile(String userId, UpdateProfileRequest req) {
        var user = userRepo.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在"));

        if (req.nickname() != null) {
            if (req.nickname().length() < 2 || req.nickname().length() > 20) {
                throw new BusinessException(ErrorCode.UNKNOWN, "昵称需2-20字符");
            }
            user.setNickname(req.nickname());
        }
        if (req.signature() != null) {
            if (req.signature().length() > 200) {
                throw new BusinessException(ErrorCode.UNKNOWN, "签名不能超过200字符");
            }
            user.setSignature(req.signature());
        }
        if (req.avatarUrl() != null) {
            user.setAvatarUrl(req.avatarUrl());
        }
        userRepo.save(user);
        return UserSummary.from(user);
    }

    // ─── Avatar Upload ────────────────────────────────────

    public AvatarUploadResponse uploadAvatar(MultipartFile file) {
        String path = fileStorageService.storeImage(file);
        return new AvatarUploadResponse(baseUrl + path);
    }

    // ─── Change Password ──────────────────────────────────

    @Transactional
    public ChangePasswordResponse changePassword(String userId, ChangePasswordRequest req) {
        var user = userRepo.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在"));

        if (!passwordEncoder.matches(req.oldPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_WRONG, "旧密码错误");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepo.save(user);

        return new ChangePasswordResponse("密码已修改，所有已登录会话已强制下线", 0);
    }

    // ─── Deactivate Account ───────────────────────────────

    @Transactional
    public DeactivateAccountResponse deactivateAccount(String username, String userId, String password) {
        var user = userRepo.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在"));

        if (!user.getUsername().equals(username)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "只能注销自己的账号");
        }

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_WRONG, "密码错误");
        }

        Instant now = Instant.now();
        user.setDeactivated(true);
        user.setDeactivatedAt(now);
        user.setRecoveryDeadline(now.plusSeconds(30L * 24 * 3600));
        user.setTokenVersion(user.getTokenVersion() + 1);
        // Clear personal info but keep username/email for recovery
        user.setAvatarUrl("");
        user.setSignature("");
        userRepo.save(user);

        return new DeactivateAccountResponse(now, user.getRecoveryDeadline());
    }

    // ─── Recover Account ──────────────────────────────────

    @Transactional
    public void recoverAccount(String username, RecoverAccountRequest req) {
        var user = userRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "账号不存在或已超过恢复期"));

        if (!user.isDeactivated()) {
            throw new BusinessException(ErrorCode.UNKNOWN, "该账号未被注销");
        }

        if (user.getRecoveryDeadline() == null || user.getRecoveryDeadline().isBefore(Instant.now())) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "账号已超过30天恢复期，数据已彻底删除");
        }

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.PASSWORD_WRONG, "邮箱或密码错误");
        }

        user.setDeactivated(false);
        user.setDeactivatedAt(null);
        user.setRecoveryDeadline(null);
        userRepo.save(user);
    }

    // ─── Data Export ──────────────────────────────────────

    @Transactional
    public ExportDataResponse exportData(String username, String userId, ExportDataRequest req) {
        if (!userRepo.findById(userId).map(u -> u.getUsername().equals(username)).orElse(false)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "只能导出自己的数据");
        }

        // rate limit: 24h max 1
        String rateKey = "export:limit:" + userId + ":24h";
        Long count = redis.opsForValue().increment(rateKey);
        if (count != null && count == 1) {
            redis.expire(rateKey, 24, TimeUnit.HOURS);
        }
        if (count != null && count > 1) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "导出频率过高，请24小时后再试");
        }

        var task = new UserExportTask(UUID.randomUUID().toString(), userId, req.scope());
        exportTaskRepo.save(task);

        return new ExportDataResponse(task.getId(), task.getStatus(), null, null, 0, task.getCreatedAt());
    }

    public ExportDataResponse getExportStatus(String username, String userId, String taskId) {
        var task = exportTaskRepo.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "导出任务不存在"));

        return new ExportDataResponse(task.getId(), task.getStatus(),
                task.getDownloadUrl(), task.getExpiresAt(), task.getProgress(), task.getCreatedAt());
    }

    // ─── Login History ────────────────────────────────────

    public PaginatedResponse<LoginRecord> getLoginHistory(String userId, int page, int pageSize) {
        Pageable pageable = PageRequest.of(page - 1, pageSize);
        var historyPage = loginHistoryRepo.findByUserIdOrderByCreatedAtDesc(userId, pageable);

        var items = historyPage.getContent().stream()
                .map(h -> new LoginRecord(h.getId(), h.getLoginType(),
                        h.getIpAddress(), h.getUserAgent(), h.isSuccess(), h.getCreatedAt()))
                .toList();

        return PaginatedResponse.of(items, historyPage.getTotalElements(), page, pageSize);
    }

    // ─── Browsing History Recording ───────────────────────

    @Transactional
    public void recordBrowsing(String userId, String postId) {
        // dedup within 1h
        var existing = browsingHistoryRepo.findByUserIdAndPostId(userId, postId);
        if (existing.isPresent()) {
            var bh = existing.get();
            if (bh.getViewedAt().isAfter(Instant.now().minusSeconds(3600))) {
                bh.setViewedAt(Instant.now());
                browsingHistoryRepo.save(bh);
                return;
            }
            bh.setViewedAt(Instant.now());
            browsingHistoryRepo.save(bh);
            return;
        }

        var bh = new BrowsingHistory(UUID.randomUUID().toString(), userId, postId);
        browsingHistoryRepo.save(bh);

        // enforce 30-day / 200-record caps
        Instant cutoff = Instant.now().minusSeconds(30L * 24 * 3600);
        browsingHistoryRepo.deleteOldRecords(userId, cutoff);

        long count = browsingHistoryRepo.countByUserId(userId);
        if (count > 200) {
            // delete oldest beyond 200
            browsingHistoryRepo.deleteExcessRecords(userId, 200);
        }
    }

    // ─── Helpers ──────────────────────────────────────────

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) return "";
        int at = email.indexOf('@');
        if (at <= 1) return email;
        return email.charAt(0) + "***" + email.substring(at);
    }
}
