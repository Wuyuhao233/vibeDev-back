package com.vibedev.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.common.PaginatedResponse;
import com.vibedev.dto.board.*;
import com.vibedev.entity.*;
import com.vibedev.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class BoardService {

    private static final Logger log = LoggerFactory.getLogger(BoardService.class);
    private static final String BOARDS_CACHE_KEY = "boards:all";
    private static final int BOARDS_CACHE_TTL_MINUTES = 30;

    private final BoardRepository boardRepo;
    private final TagRepository tagRepo;
    private final PostRepository postRepo;
    private final PostTagRepository postTagRepo;
    private final UserRepository userRepo;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public BoardService(BoardRepository boardRepo, TagRepository tagRepo,
                        PostRepository postRepo, PostTagRepository postTagRepo,
                        UserRepository userRepo, StringRedisTemplate redis,
                        ObjectMapper objectMapper) {
        this.boardRepo = boardRepo;
        this.tagRepo = tagRepo;
        this.postRepo = postRepo;
        this.postTagRepo = postTagRepo;
        this.userRepo = userRepo;
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public List<BoardItem> listAll() {
        // Try cache first
        try {
            String cached = redis.opsForValue().get(BOARDS_CACHE_KEY);
            if (cached != null && !cached.isBlank()) {
                return objectMapper.readValue(cached, new TypeReference<List<BoardItem>>() {});
            }
        } catch (Exception e) {
            log.warn("Failed to read boards cache: {}", e.getMessage());
        }

        var boards = boardRepo.findByStatusAndIsDeletedFalseOrderBySortOrder("active");
        var items = boards.stream()
                .filter(b -> !"关注".equals(b.getName()))
                .map(this::toBoardItem)
                .toList();

        // Cache
        try {
            redis.opsForValue().set(BOARDS_CACHE_KEY,
                    objectMapper.writeValueAsString(items),
                    BOARDS_CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("Failed to cache boards: {}", e.getMessage());
        }

        return items;
    }

    public BoardItem getBoardDetail(String id) {
        var board = boardRepo.findByIdAndIsDeletedFalse(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "版块不存在"));
        return toBoardItem(board);
    }

    public List<TagItem> getBoardTags(String boardId) {
        var board = boardRepo.findByIdAndIsDeletedFalse(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "版块不存在"));
        return tagRepo.findByBoardIdOrderBySortOrder(boardId).stream()
                .map(this::toTagItem)
                .toList();
    }

    public PaginatedResponse<PostCard> getBoardPosts(String boardId, String tagId,
                                                      String sort, int page, int limit) {
        var board = boardRepo.findByIdAndIsDeletedFalse(boardId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "版块不存在"));

        if (page < 1) page = 1;
        if (limit < 1 || limit > 50) limit = 50;
        Pageable pageable = PageRequest.of(page - 1, limit);

        var postsPage = switch (sort != null ? sort : "hot") {
            case "latest" -> postRepo.findByBoardIdLatest(boardId, tagId, pageable);
            case "trending" -> postRepo.findByBoardIdTrending(boardId, tagId,
                    Instant.now().minusSeconds(86400), pageable);
            default -> postRepo.findByBoardIdHot(boardId, tagId, pageable);
        };

        var postIds = postsPage.getContent().stream().map(Post::getId).toList();
        var tagsByPostId = buildTagsByPostId(postIds);
        var usersById = buildUsersById(postsPage.getContent().stream()
                .map(Post::getAuthorId).distinct().toList());

        var items = postsPage.getContent().stream()
                .map(p -> toPostCard(p, tagsByPostId.getOrDefault(p.getId(), Collections.emptyList()),
                        usersById.get(p.getAuthorId()), board.getName()))
                .toList();

        return PaginatedResponse.of(items, postsPage.getTotalElements(), page, limit);
    }

    // ─── Helpers ──────────────────────────────────────────

    private BoardItem toBoardItem(Board b) {
        var tags = tagRepo.findByBoardIdOrderBySortOrder(b.getId()).stream()
                .map(this::toTagItem)
                .toList();
        return new BoardItem(b.getId(), b.getName(), b.getIcon(), b.getDescription(),
                b.getSortOrder(), b.getStatus(), b.getPostCount(), b.getCreatedAt(), tags);
    }

    private TagItem toTagItem(Tag t) {
        return new TagItem(t.getId(), t.getName(), t.getBoardId(), t.getSortOrder(), t.getPostCount());
    }

    public PostCard toPostCard(Post p, List<TagItem> tags, User author, String boardName) {
        String summary = "";
        if (p.getContentMarkdown() != null) {
            String plain = p.getContentMarkdown()
                    .replaceAll("```[\\s\\S]*?```", " ")
                    .replaceAll("`[^`]*`", " ")
                    .replaceAll("!\\[.*?]\\(.*?\\)", " ")
                    .replaceAll("\\[([^]]*)]\\(.*?\\)", "$1")
                    .replaceAll("[*#>`~|_\\-]+", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            summary = plain.length() > 200 ? plain.substring(0, 200) : plain;
        }

        var authorDto = author != null
                ? new PostCardAuthor(author.getUsername(), author.getNickname(),
                    author.getAvatarUrl(), author.getLevel())
                : new PostCardAuthor("unknown", "未知用户", "", 1);

        return new PostCard(p.getId(), p.getTitle(), summary, p.getCoverImageUrl(),
                authorDto, p.getCreatedAt(), p.getLikeCount(), p.getReplyCount(),
                p.getCollectCount(), tags, p.isPinned(), p.isEssence(), boardName);
    }

    public Map<String, List<TagItem>> buildTagsByPostId(List<String> postIds) {
        if (postIds.isEmpty()) return Collections.emptyMap();
        var postTags = postTagRepo.findByPostIdIn(postIds);
        var tagIds = postTags.stream().map(PostTag::getTagId).distinct().toList();
        if (tagIds.isEmpty()) return Collections.emptyMap();
        var tags = tagRepo.findAllById(tagIds);
        var tagMap = tags.stream().collect(Collectors.toMap(Tag::getId, this::toTagItem));
        return postTags.stream()
                .collect(Collectors.groupingBy(PostTag::getPostId,
                         Collectors.mapping(pt -> tagMap.get(pt.getTagId()),
                                 Collectors.collectingAndThen(Collectors.toList(),
                                         list -> list.stream()
                                                 .filter(t -> t != null)
                                                 .toList()))));
    }

    private Map<String, User> buildUsersById(List<String> userIds) {
        if (userIds.isEmpty()) return Collections.emptyMap();
        return userRepo.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
    }
}
