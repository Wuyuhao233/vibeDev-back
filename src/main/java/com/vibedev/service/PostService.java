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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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
    private final CollectionFolderRepository collectionFolderRepo;
    private final LikeRepository likeRepo;
    private final NotificationService notificationService;
    private final org.springframework.data.redis.core.StringRedisTemplate redis;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Value("${app.upload.path:./uploads}")
    private String uploadPath;

    public PostService(PostRepository postRepo, PostTagRepository postTagRepo,
                       TagRepository tagRepo, UserRepository userRepo,
                       BoardRepository boardRepo, FavoriteRepository favoriteRepo,
                       CollectionFolderRepository collectionFolderRepo,
                       LikeRepository likeRepo,
                       NotificationService notificationService,
                       org.springframework.data.redis.core.StringRedisTemplate redis,
                       com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.postRepo = postRepo;
        this.postTagRepo = postTagRepo;
        this.tagRepo = tagRepo;
        this.userRepo = userRepo;
        this.boardRepo = boardRepo;
        this.favoriteRepo = favoriteRepo;
        this.collectionFolderRepo = collectionFolderRepo;
        this.likeRepo = likeRepo;
        this.notificationService = notificationService;
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

            // Send post_essenced notification to post author
            notificationService.create(
                    post.getAuthorId(),
                    "post_essenced",
                    "帖子被加精",
                    "你的帖子《" + post.getTitle() + "》被版主设为精华",
                    "post",
                    postId
            );
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
    public CollectToggleResponse collect(String postId, String userId, String folderId) {
        var post = postRepo.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在"));
        if (post.isDeleted()) {
            throw new BusinessException(ErrorCode.POST_DELETED, "该帖子已被删除");
        }

        // Validate folder ownership if provided
        if (folderId != null) {
            var folder = collectionFolderRepo.findByIdAndUserId(folderId, userId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "收藏夹不存在"));
        }

        var existing = favoriteRepo.findByUserIdAndPostId(userId, postId);
        if (existing.isPresent()) {
            throw new BusinessException(ErrorCode.DUPLICATE_SUBMIT, "已收藏该帖子");
        }

        var fav = new Favorite();
        fav.setId(UUID.randomUUID().toString());
        fav.setUserId(userId);
        fav.setPostId(postId);
        fav.setCollectionFolderId(folderId);
        favoriteRepo.save(fav);

        post.setCollectCount(post.getCollectCount() + 1);
        postRepo.save(post);

        // Send post_collected notification to post author (if not self-collect)
        if (!userId.equals(post.getAuthorId())) {
            var collector = userRepo.findById(userId).orElse(null);
            String collectorName = collector != null ? collector.getUsername() : "unknown";
            notificationService.create(
                    post.getAuthorId(),
                    "post_collected",
                    "帖子被收藏",
                    collectorName + " 收藏了你的帖子《" + post.getTitle() + "》",
                    "post",
                    postId
            );
        }

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

    public ShareCardGenerateResponse generateShareCard(String postId) {
        var post = postRepo.findByIdAndIsDeletedFalse(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "帖子不存在"));

        String description = extractPlainText(post.getContentMarkdown());
        var author = userRepo.findById(post.getAuthorId()).orElse(null);
        String authorName = author != null ? author.getUsername() : "unknown";

        try {
            String filename = postId + "_" + Instant.now().toEpochMilli() + ".png";
            String dateDir = LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy/MM"));
            Path dir = Paths.get(uploadPath, "share-cards", dateDir);
            Files.createDirectories(dir);

            BufferedImage card = renderShareCard(post.getTitle(), description,
                    post.getCoverImageUrl(), authorName);
            Path filePath = dir.resolve(filename);
            ImageIO.write(card, "png", filePath.toFile());

            String imageUrl = "/uploads/share-cards/" + dateDir + "/" + filename;
            return new ShareCardGenerateResponse(imageUrl, 1200, 630);
        } catch (IOException e) {
            log.error("Failed to generate share card for post {}", postId, e);
            throw new BusinessException(10000, "分享卡片生成失败");
        }
    }

    private BufferedImage renderShareCard(String title, String description,
                                           String coverUrl, String authorName) throws IOException {
        int width = 1200;
        int height = 630;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        // Enable anti-aliasing
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // Background gradient
        GradientPaint bgGradient = new GradientPaint(0, 0, new Color(30, 30, 50),
                width, height, new Color(15, 15, 35));
        g.setPaint(bgGradient);
        g.fillRect(0, 0, width, height);

        // Left content area
        int contentX = 60;
        int contentY = 60;
        int contentWidth = 740;

        // Cover image (if available) - top right area
        if (coverUrl != null && !coverUrl.isBlank()) {
            try {
                BufferedImage cover = readImage(coverUrl);
                if (cover != null) {
                    int coverH = 280;
                    int coverW = 420;
                    int coverX = width - coverW - 60;
                    int coverY = 60;
                    g.drawImage(cover, coverX, coverY, coverW, coverH, null);
                    // Rounded border
                    g.setColor(new Color(255, 255, 255, 40));
                    g.setStroke(new BasicStroke(2));
                    g.drawRoundRect(coverX, coverY, coverW, coverH, 16, 16);
                }
            } catch (Exception e) {
                log.debug("Failed to load cover image: {}", e.getMessage());
            }
        }

        // Title
        g.setColor(Color.WHITE);
        Font titleFont = new Font("SansSerif", Font.BOLD, 42);
        g.setFont(titleFont);
        List<String> titleLines = wrapText(title, titleFont, g, contentWidth);
        int lineY = contentY + 50;
        for (String line : titleLines) {
            if (lineY > contentY + 200) break; // max 3 lines
            g.drawString(line, contentX, lineY);
            lineY += 56;
        }

        // Description
        if (description != null && !description.isBlank()) {
            g.setColor(new Color(200, 200, 220));
            Font descFont = new Font("SansSerif", Font.PLAIN, 24);
            g.setFont(descFont);
            List<String> descLines = wrapText(description, descFont, g, contentWidth);
            lineY += 20;
            for (int i = 0; i < Math.min(descLines.size(), 4); i++) {
                g.drawString(descLines.get(i), contentX, lineY);
                lineY += 34;
            }
        }

        // Author name
        g.setColor(new Color(150, 150, 180));
        g.setFont(new Font("SansSerif", Font.PLAIN, 20));
        g.drawString("— " + authorName, contentX, height - 80);

        // QR placeholder (bottom right)
        int qrSize = 120;
        int qrX = width - qrSize - 80;
        int qrY = height - qrSize - 80;
        g.setColor(new Color(255, 255, 255, 30));
        g.fillRoundRect(qrX - 10, qrY - 10, qrSize + 20, qrSize + 20, 12, 12);
        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 14));
        g.drawString("扫码查看", qrX + 28, qrY - 20);
        // Simple QR pattern
        drawQrPlaceholder(g, qrX, qrY, qrSize);

        g.dispose();
        return image;
    }

    private void drawQrPlaceholder(Graphics2D g, int x, int y, int size) {
        int modules = 7;
        int moduleSize = size / modules;
        g.setColor(new Color(255, 255, 255, 80));
        for (int r = 0; r < modules; r++) {
            for (int c = 0; c < modules; c++) {
                if ((r * modules + c) % 3 != 0) {
                    g.fillRect(x + c * moduleSize, y + r * moduleSize, moduleSize, moduleSize);
                }
            }
        }
    }

    private List<String> wrapText(String text, Font font, Graphics2D g, int maxWidth) {
        List<String> lines = new ArrayList<>();
        FontRenderContext frc = g.getFontRenderContext();
        String[] words = text.split("");
        StringBuilder line = new StringBuilder();
        for (String ch : words) {
            String test = line + ch;
            Rectangle2D bounds = font.getStringBounds(test, frc);
            if (bounds.getWidth() > maxWidth && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(ch);
            } else {
                line.append(ch);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        return lines;
    }

    private BufferedImage readImage(String urlStr) {
        try {
            if (urlStr.startsWith("http")) {
                try (InputStream in = new URL(urlStr).openStream()) {
                    return ImageIO.read(in);
                }
            } else {
                Path p = Paths.get(uploadPath, urlStr.replace("/uploads/", ""));
                if (Files.exists(p)) {
                    return ImageIO.read(p.toFile());
                }
            }
        } catch (Exception e) {
            log.debug("Cannot read image from {}: {}", urlStr, e.getMessage());
        }
        return null;
    }

    private String extractPlainText(String markdown) {
        if (markdown == null) return "";
        String plain = markdown
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("`[^`]*`", " ")
                .replaceAll("!\\[.*?]\\(.*?\\)", " ")
                .replaceAll("\\[([^]]*)]\\(.*?\\)", "$1")
                .replaceAll("[*#>`~|_\\-]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return plain.length() > 200 ? plain.substring(0, 200) : plain;
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
            isLiked = likeRepo.existsByUserIdAndTargetTypeAndTargetId(currentUserId, "post", p.getId());
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
