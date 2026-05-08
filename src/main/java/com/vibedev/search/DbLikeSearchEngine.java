package com.vibedev.search;

import com.vibedev.dto.board.TagItem;
import com.vibedev.dto.search.SearchResponse;
import com.vibedev.dto.search.SearchResultItem;
import com.vibedev.dto.user.UserSummary;
import com.vibedev.entity.PostTag;
import com.vibedev.entity.Tag;
import com.vibedev.repository.PostTagRepository;
import com.vibedev.repository.TagRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

public class DbLikeSearchEngine implements SearchEngine {

    private static final Logger log = LoggerFactory.getLogger(DbLikeSearchEngine.class);
    private static final String PRE_TAG = "<mark>";
    private static final String POST_TAG = "</mark>";
    private static final int EXCERPT_LENGTH = 150;

    private final JdbcTemplate jdbc;
    private final PostTagRepository postTagRepo;
    private final TagRepository tagRepo;

    public DbLikeSearchEngine(JdbcTemplate jdbc, PostTagRepository postTagRepo, TagRepository tagRepo) {
        this.jdbc = jdbc;
        this.postTagRepo = postTagRepo;
        this.tagRepo = tagRepo;
    }

    @Override
    public SearchResponse search(SearchQuery query) {
        long start = System.currentTimeMillis();
        String escapedKeyword = SearchUtils.escapeLikeWildcards(query.keyword());
        String likePattern = "%" + escapedKeyword + "%";

        List<Object> params = new ArrayList<>();
        String whereClause = buildWhere(query, params, likePattern);
        Object[] paramArray = params.toArray();

        String countSql = "SELECT COUNT(*) FROM posts p WHERE " + whereClause;
        Long total = jdbc.queryForObject(countSql, Long.class, paramArray);
        if (total == null) total = 0L;

        String selectPart = """
                p.id, p.title, p.content_markdown, p.like_count, p.reply_count,
                p.collect_count, p.is_essence, p.created_at, p.board_id,
                b.name AS board_name,
                u.id AS user_id, u.username, u.nickname, u.avatar_url, u.level
                """;

        String sql = "SELECT " + selectPart + " FROM posts p " +
                "JOIN boards b ON p.board_id = b.id " +
                "JOIN users u ON p.author_id = u.id " +
                "WHERE " + whereClause +
                " ORDER BY p.created_at DESC LIMIT " + query.limit() +
                " OFFSET " + ((query.page() - 1) * query.limit());

        List<SearchResultItem> items = jdbc.query(sql, (rs, rowNum) -> mapRow(rs), paramArray);

        // Apply highlighting and attach tags
        items = applyHighlighting(items, query.keyword());
        items = attachTags(items);

        double searchTimeMs = System.currentTimeMillis() - start;
        String suggestion = total == 0 ? "换个关键词试试" : null;

        return new SearchResponse(items, total, query.page(), query.limit(),
                formatSearchTime(searchTimeMs), suggestion);
    }

    private String buildWhere(SearchQuery query, List<Object> params, String likePattern) {
        StringBuilder where = new StringBuilder();
        params.add(likePattern);

        switch (query.scope()) {
            case TITLE_ONLY:
                where.append("p.title LIKE ?");
                break;
            case BOARD:
            case ALL:
            case TITLE_CONTENT:
            default:
                where.append("(p.title LIKE ? OR p.content_markdown LIKE ?)");
                params.add(likePattern);
                break;
        }

        where.append(" AND p.is_deleted = FALSE AND p.audit_status = 'approved'");

        if (query.scope() == SearchScope.BOARD && query.boardId() != null) {
            where.append(" AND p.board_id = ?");
            params.add(query.boardId());
        }
        return where.toString();
    }

    private SearchResultItem mapRow(ResultSet rs) throws SQLException {
        UserSummary author = new UserSummary(
                rs.getString("user_id"),
                rs.getString("username"),
                rs.getString("nickname"),
                rs.getString("avatar_url"),
                rs.getInt("level"),
                null
        );
        return new SearchResultItem(
                rs.getString("id"),
                rs.getString("title"),
                null, // placeholder
                rs.getString("content_markdown"), // placeholder
                rs.getString("board_name"),
                rs.getString("board_id"),
                author,
                List.of(),
                rs.getInt("like_count"),
                rs.getInt("reply_count"),
                rs.getInt("collect_count"),
                rs.getBoolean("is_essence"),
                toInstant(rs.getTimestamp("created_at"))
        );
    }

    private List<SearchResultItem> applyHighlighting(List<SearchResultItem> items, String keyword) {
        List<SearchResultItem> highlighted = new ArrayList<>(items.size());
        for (SearchResultItem item : items) {
            highlighted.add(new SearchResultItem(
                    item.id(), item.title(),
                    SearchUtils.highlight(item.title(), keyword, PRE_TAG, POST_TAG),
                    SearchUtils.excerpt(item.contentExcerpt(), keyword, EXCERPT_LENGTH, PRE_TAG, POST_TAG),
                    item.boardName(), item.boardId(), item.author(),
                    item.tags(),
                    item.likeCount(), item.replyCount(), item.collectCount(),
                    item.isEssenced(), item.createdAt()));
        }
        return highlighted;
    }

    private List<SearchResultItem> attachTags(List<SearchResultItem> items) {
        if (items.isEmpty()) return items;
        List<String> postIds = items.stream().map(SearchResultItem::id).toList();
        List<PostTag> postTags = postTagRepo.findByPostIdIn(postIds);

        List<String> tagIds = postTags.stream().map(PostTag::getTagId).distinct().toList();
        Map<String, Tag> tagMap = tagRepo.findAllById(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, t -> t));

        Map<String, List<TagItem>> postTagMap = postTags.stream()
                .collect(Collectors.groupingBy(PostTag::getPostId,
                        Collectors.mapping(pt -> {
                            Tag t = tagMap.get(pt.getTagId());
                            if (t == null) return null;
                            return new TagItem(t.getId(), t.getName(), t.getBoardId(),
                                    t.getSortOrder(), t.getPostCount());
                        }, Collectors.toList())));

        List<SearchResultItem> enriched = new ArrayList<>(items.size());
        for (SearchResultItem item : items) {
            List<TagItem> tags = postTagMap.getOrDefault(item.id(), List.of()).stream()
                    .filter(Objects::nonNull).toList();
            enriched.add(new SearchResultItem(
                    item.id(), item.title(), item.titleHighlighted(), item.contentExcerpt(),
                    item.boardName(), item.boardId(), item.author(),
                    tags,
                    item.likeCount(), item.replyCount(), item.collectCount(),
                    item.isEssenced(), item.createdAt()));
        }
        return enriched;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }

    private static String formatSearchTime(double ms) {
        return String.format("%.3fs", ms / 1000.0);
    }
}
