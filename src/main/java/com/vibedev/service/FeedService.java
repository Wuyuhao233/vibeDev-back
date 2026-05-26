package com.vibedev.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibedev.common.PaginatedResponse;
import com.vibedev.dto.board.*;
import com.vibedev.entity.*;
import com.vibedev.repository.*;
import com.vibedev.service.FollowService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class FeedService {

    private static final Logger log = LoggerFactory.getLogger(FeedService.class);
    private static final String USER_PROFILE_CACHE_PREFIX = "user_profile:";
    private static final String RECOMMEND_CACHE_PREFIX = "feed:recommend:";
    private static final int USER_PROFILE_CACHE_TTL_MINUTES = 10;
    private static final int RECOMMEND_CACHE_TTL_MINUTES = 2;
    private static final int CANDIDATE_LIMIT = 500;

    private final PostRepository postRepo;
    private final PostTagRepository postTagRepo;
    private final TagRepository tagRepo;
    private final UserRepository userRepo;
    private final BoardRepository boardRepo;
    private final BoardService boardService;
    private final UserFollowedTagRepository userFollowedTagRepo;
    private final BrowsingHistoryRepository browsingHistoryRepo;
    private final SystemConfigRepository systemConfigRepo;
    private final FollowService followService;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public FeedService(PostRepository postRepo, PostTagRepository postTagRepo,
                       TagRepository tagRepo, UserRepository userRepo,
                       BoardRepository boardRepo, BoardService boardService,
                       UserFollowedTagRepository userFollowedTagRepo,
                       BrowsingHistoryRepository browsingHistoryRepo,
                       SystemConfigRepository systemConfigRepo,
                       FollowService followService,
                       StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.postRepo = postRepo;
        this.postTagRepo = postTagRepo;
        this.tagRepo = tagRepo;
        this.userRepo = userRepo;
        this.boardRepo = boardRepo;
        this.boardService = boardService;
        this.userFollowedTagRepo = userFollowedTagRepo;
        this.browsingHistoryRepo = browsingHistoryRepo;
        this.systemConfigRepo = systemConfigRepo;
        this.followService = followService;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    // ─── Trending feed ─────────────────────────────────────

    public PaginatedResponse<PostCard> feedTrending(int page, int limit, String currentUserId) {
        if (page < 1) page = 1;
        if (limit < 1 || limit > 50) limit = 20;
        Pageable pageable = PageRequest.of(page - 1, limit);

        var postsPage = postRepo.findHotPosts(pageable);
        return toPostCardPage(postsPage, page, limit, currentUserId);
    }

    // ─── Recommend feed (V1.1 rule engine) ─────────────────

    public PaginatedResponse<PostCard> feedRecommend(String userId, int page, int limit) {
        if (userId == null) {
            return feedTrending(page, limit, null);
        }
        if (isColdStart(userId)) {
            return feedTrending(page, limit, userId);
        }

        if (page < 1) page = 1;
        if (limit < 1 || limit > 50) limit = 20;

        // Try recommendation cache
        String cacheKey = RECOMMEND_CACHE_PREFIX + userId + ":" + page;
        PaginatedResponse<PostCard> cached = tryGetRecommendCache(cacheKey);
        if (cached != null) return cached;

        try {
            UserTagProfile profile = loadUserTagProfile(userId);
            RecommendationWeights weights = loadWeights();

            List<Post> candidates = postRepo.findTrending(
                    Instant.now().minusSeconds(7 * 86400),
                    PageRequest.of(0, CANDIDATE_LIMIT)).getContent();

            if (candidates.isEmpty()) {
                var empty = PaginatedResponse.<PostCard>of(Collections.emptyList(), 0, page, limit);
                cacheRecommend(cacheKey, empty);
                return empty;
            }

            List<String> postIds = candidates.stream().map(Post::getId).toList();
            List<String> authorIds = candidates.stream().map(Post::getAuthorId).distinct().toList();
            Map<String, List<TagItem>> tagsByPostId = boardService.buildTagsByPostId(postIds);
            Map<String, User> usersById = buildUsersById(authorIds);
            Map<String, String> boardsById = buildBoardsById(
                    candidates.stream().map(Post::getBoardId).distinct().toList());

            double maxHeat = candidates.stream().mapToDouble(Post::getHeatScore).max().orElse(1);
            double minHeat = candidates.stream().mapToDouble(Post::getHeatScore).min().orElse(0);

            Instant now = Instant.now();
            List<ScoredPost> scored = candidates.stream()
                    .map(p -> {
                        List<TagItem> postTags = tagsByPostId.getOrDefault(p.getId(), Collections.emptyList());
                        User author = usersById.get(p.getAuthorId());
                        double score = computeScore(p, postTags, author, profile, weights, maxHeat, minHeat, now);
                        return new ScoredPost(p, postTags, author, boardsById.getOrDefault(p.getBoardId(), ""), score);
                    })
                    .sorted(Comparator.comparingDouble(ScoredPost::score).reversed())
                    .toList();

            int fromIndex = (page - 1) * limit;
            if (fromIndex >= scored.size()) {
                var empty = PaginatedResponse.<PostCard>of(Collections.emptyList(), scored.size(), page, limit);
                cacheRecommend(cacheKey, empty);
                return empty;
            }
            int toIndex = Math.min(fromIndex + limit, scored.size());
            List<ScoredPost> paged = scored.subList(fromIndex, toIndex);

            List<PostCard> items = paged.stream()
                    .map(sp -> boardService.toPostCard(sp.post, sp.tags, sp.author, sp.boardName, userId))
                    .toList();

            var result = PaginatedResponse.of(items, scored.size(), page, limit);
            cacheRecommend(cacheKey, result);
            return result;
        } catch (Exception e) {
            log.warn("Recommend engine failed, degrading to trending: {}", e.getMessage());
            return feedTrending(page, limit, userId);
        }
    }

    // ─── Following feed (V1.1 → V2: user follow) ────────────

    public PaginatedResponse<PostCard> feedFollowing(String userId, int page, int limit) {
        if (userId == null) {
            return PaginatedResponse.of(Collections.emptyList(), 0, page, limit);
        }

        List<String> followingIds = followService.getFollowingIds(userId);
        if (followingIds.isEmpty()) {
            return PaginatedResponse.of(Collections.emptyList(), 0, page, limit);
        }

        if (page < 1) page = 1;
        if (limit < 1 || limit > 50) limit = 20;

        var postsPage = postRepo.findByAuthorIdIn(followingIds, PageRequest.of(page - 1, limit));
        return toPostCardPage(postsPage, page, limit, userId);
    }

    // ─── Scoring logic ─────────────────────────────────────

    private double computeScore(Post p, List<TagItem> postTags, User author,
                                UserTagProfile profile, RecommendationWeights w,
                                double maxHeat, double minHeat, Instant now) {
        double tagMatchScore = computeTagMatchScore(postTags, profile);
        double heatScore = normalizeHeat(p.getHeatScore(), minHeat, maxHeat);
        double freshnessScore = computeFreshnessScore(p.getCreatedAt(), now);
        double qualityScore = computeQualityScore(p, author);

        return tagMatchScore * w.tagMatch()
                + heatScore * w.heat()
                + freshnessScore * w.freshness()
                + qualityScore * w.quality();
    }

    private double computeTagMatchScore(List<TagItem> postTags, UserTagProfile profile) {
        if (postTags.isEmpty()) return 10;
        Set<String> tagIds = postTags.stream().map(TagItem::id).collect(Collectors.toSet());

        if (profile.followed().stream().anyMatch(tagIds::contains)) return 80;
        if (profile.browsedTop3().stream().anyMatch(tagIds::contains)) return 60;
        if (profile.activity().stream().anyMatch(tagIds::contains)) return 40;

        return 10;
    }

    private double normalizeHeat(double heatScore, double minHeat, double maxHeat) {
        if (maxHeat <= minHeat) return 50;
        return Math.min(100, Math.max(0, (heatScore - minHeat) / (maxHeat - minHeat) * 100));
    }

    private double computeFreshnessScore(Instant createdAt, Instant now) {
        long hours = Duration.between(createdAt, now).toHours();
        return Math.max(0, 100 - hours * 2.0);
    }

    private double computeQualityScore(Post p, User author) {
        double score = 0;
        if (author != null && author.getLevel() >= 5) score += 20;
        if (p.isHasCodeBlock()) score += 10;
        if (p.getContentLength() > 200) score += 10;
        if (p.getCoverImageUrl() != null && !p.getCoverImageUrl().isBlank()) score += 5;
        if (p.isEssence()) score += 30;
        return Math.min(100, score);
    }

    // ─── User tag profile ──────────────────────────────────

    UserTagProfile loadUserTagProfile(String userId) {
        String cacheKey = USER_PROFILE_CACHE_PREFIX + userId + ":tags";
        try {
            String cached = redis.opsForValue().get(cacheKey);
            if (cached != null && !cached.isBlank()) {
                return objectMapper.readValue(cached, UserTagProfile.class);
            }
        } catch (Exception e) {
            log.debug("User profile cache read failed: {}", e.getMessage());
        }

        List<String> followed = userFollowedTagRepo.findByUserId(userId).stream()
                .map(UserFollowedTag::getTagId).toList();
        List<String> browsedTop3 = computeBrowsedTop3(userId);
        List<String> activity = computeActivityTags(userId);

        var profile = new UserTagProfile(followed, browsedTop3, activity);

        try {
            redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(profile),
                    USER_PROFILE_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.debug("User profile cache write failed: {}", e.getMessage());
        }

        return profile;
    }

    private List<String> computeBrowsedTop3(String userId) {
        Instant weekAgo = Instant.now().minusSeconds(7 * 86400);
        List<String> postIds = browsingHistoryRepo.findPostIdsByUserIdAndViewedAtAfter(userId, weekAgo);
        if (postIds.isEmpty()) return Collections.emptyList();

        List<PostTag> postTags = postTagRepo.findByPostIdIn(postIds);
        return topTagIds(postTags, 3);
    }

    private List<String> computeActivityTags(String userId) {
        var userPostsPage = postRepo.findByAuthorIdAndIsDeletedFalseOrderByCreatedAtDesc(
                userId, PageRequest.of(0, 50));
        List<Post> userPosts = userPostsPage.getContent();
        if (userPosts.isEmpty()) return Collections.emptyList();

        List<String> postIds = userPosts.stream().map(Post::getId).toList();
        List<PostTag> postTags = postTagRepo.findByPostIdIn(postIds);
        return topTagIds(postTags, 3);
    }

    private static List<String> topTagIds(List<PostTag> postTags, int limit) {
        return postTags.stream()
                .collect(Collectors.groupingBy(PostTag::getTagId, Collectors.counting()))
                .entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    // ─── Cold start ────────────────────────────────────────

    boolean isColdStart(String userId) {
        return userRepo.findById(userId)
                .map(user -> {
                    boolean newUser = user.getCreatedAt().isAfter(Instant.now().minusSeconds(7 * 86400));
                    boolean noHistory = browsingHistoryRepo.countByUserId(userId) == 0;
                    return newUser && noHistory;
                })
                .orElse(true);
    }

    // ─── Weights loading ───────────────────────────────────

    RecommendationWeights loadWeights() {
        try {
            var config = systemConfigRepo.findByConfigKey("recommendation_weights");
            if (config.isPresent()) {
                return objectMapper.readValue(config.get().getConfigValue(), RecommendationWeights.class);
            }
        } catch (Exception e) {
            log.warn("Failed to load recommendation weights: {}", e.getMessage());
        }
        return RecommendationWeights.DEFAULTS;
    }

    // ─── Redis cache helpers ───────────────────────────────

    @SuppressWarnings("unchecked")
    private PaginatedResponse<PostCard> tryGetRecommendCache(String cacheKey) {
        try {
            String cached = redis.opsForValue().get(cacheKey);
            if (cached != null && !cached.isBlank()) {
                return objectMapper.readValue(cached, PaginatedResponse.class);
            }
        } catch (Exception e) {
            log.debug("Recommend cache read failed: {}", e.getMessage());
        }
        return null;
    }

    private void cacheRecommend(String cacheKey, PaginatedResponse<PostCard> result) {
        try {
            redis.opsForValue().set(cacheKey, objectMapper.writeValueAsString(result),
                    RECOMMEND_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.debug("Recommend cache write failed: {}", e.getMessage());
        }
    }

    // ─── Internal value classes ────────────────────────────

    public record UserTagProfile(List<String> followed, List<String> browsedTop3, List<String> activity) {
        public UserTagProfile {
            followed = followed != null ? followed : Collections.emptyList();
            browsedTop3 = browsedTop3 != null ? browsedTop3 : Collections.emptyList();
            activity = activity != null ? activity : Collections.emptyList();
        }
    }

    public record RecommendationWeights(double tagMatch, double heat, double freshness, double quality) {
        public static final RecommendationWeights DEFAULTS = new RecommendationWeights(0.4, 0.3, 0.2, 0.1);
    }

    private record ScoredPost(Post post, List<TagItem> tags, User author, String boardName, double score) {}

    // ─── PostCard enrichment helpers ───────────────────────

    private PaginatedResponse<PostCard> toPostCardPage(
            org.springframework.data.domain.Page<Post> postsPage, int page, int limit, String currentUserId) {
        var postIds = postsPage.getContent().stream().map(Post::getId).toList();
        var tagsByPostId = boardService.buildTagsByPostId(postIds);
        var usersById = buildUsersById(postsPage.getContent().stream()
                .map(Post::getAuthorId).distinct().toList());
        var boardsById = buildBoardsById(postsPage.getContent().stream()
                .map(Post::getBoardId).distinct().toList());

        var items = postsPage.getContent().stream()
                .map(p -> boardService.toPostCard(p,
                        tagsByPostId.getOrDefault(p.getId(), Collections.emptyList()),
                        usersById.get(p.getAuthorId()),
                        boardsById.getOrDefault(p.getBoardId(), ""),
                        currentUserId))
                .toList();

        return PaginatedResponse.of(items, postsPage.getTotalElements(), page, limit);
    }

    private Map<String, User> buildUsersById(List<String> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        return userRepo.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }

    private Map<String, String> buildBoardsById(List<String> boardIds) {
        if (boardIds.isEmpty()) return Collections.emptyMap();
        return boardRepo.findAllById(boardIds).stream()
                .collect(Collectors.toMap(Board::getId, Board::getName));
    }
}
