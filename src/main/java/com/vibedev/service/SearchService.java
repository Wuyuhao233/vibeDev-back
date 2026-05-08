package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.common.ErrorCode;
import com.vibedev.dto.search.SearchResponse;
import com.vibedev.dto.search.SearchSuggestResponse;
import com.vibedev.dto.search.SearchSuggestionResponse;
import com.vibedev.entity.SearchLog;
import com.vibedev.repository.SearchLogRepository;
import com.vibedev.search.SearchEngine;
import com.vibedev.search.SearchQuery;
import com.vibedev.search.SearchScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);
    private static final int MIN_KEYWORD_LENGTH = 2;
    private static final int MAX_KEYWORD_LENGTH = 50;

    private final SearchEngine searchEngine;
    private final SearchLogRepository searchLogRepo;
    private final StringRedisTemplate redis;
    private final JdbcTemplate jdbc;

    @Value("${search.rateLimit.intervalSeconds:3}")
    private int rateLimitInterval;

    public SearchService(SearchEngine searchEngine, SearchLogRepository searchLogRepo,
                         StringRedisTemplate redis, JdbcTemplate jdbc) {
        this.searchEngine = searchEngine;
        this.searchLogRepo = searchLogRepo;
        this.redis = redis;
        this.jdbc = jdbc;
    }

    public SearchResponse search(SearchQuery query, String ipAddress, String userId) {
        validateKeyword(query.keyword());
        validateScope(query.scope(), query.boardId());
        checkRateLimit(ipAddress);

        SearchResponse result = searchEngine.search(query);

        logSearch(query.keyword(), query.scope().name().toLowerCase(),
                query.boardId(), result.total(), ipAddress, userId);

        updateHotTerms(query.keyword());

        if (result.total() == 0) {
            return new SearchResponse(result.items(), result.total(), result.page(),
                    result.pageSize(), result.searchTime(), "换个关键词试试");
        }
        return result;
    }

    public SearchSuggestionResponse getSuggestions() {
        String key = "search:hot:" + LocalDate.now();
        Set<String> hot = redis.opsForZSet().reverseRange(key, 0, 9);
        List<String> hotSearches = hot != null ? new ArrayList<>(hot) : List.of();
        return new SearchSuggestionResponse(hotSearches);
    }

    public SearchSuggestResponse getSuggest(String prefix) {
        String trimmed = prefix.trim();
        if (trimmed.isEmpty()) {
            return new SearchSuggestResponse(List.of());
        }
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            return new SearchSuggestResponse(List.of());
        }
        List<String> titles = jdbc.queryForList(
                "SELECT title FROM posts " +
                "WHERE title LIKE ? AND is_deleted = FALSE AND audit_status = 'approved' " +
                "GROUP BY title ORDER BY MAX(created_at) DESC LIMIT 10",
                String.class, trimmed + "%");
        return new SearchSuggestResponse(titles);
    }

    private void validateKeyword(String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "关键词不能为空");
        }
        String trimmed = keyword.trim();
        if (trimmed.length() < MIN_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "关键词至少2个字符");
        }
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            throw new BusinessException(ErrorCode.RATE_LIMITED, "关键词最多50个字符");
        }
    }

    private void validateScope(SearchScope scope, String boardId) {
        if (scope == SearchScope.BOARD && (boardId == null || boardId.isBlank())) {
            throw new BusinessException(ErrorCode.RATE_LIMITED,
                    "限定版块搜索需要提供board_id参数");
        }
    }

    private void checkRateLimit(String ipAddress) {
        String key = "search:rate:" + ipAddress;
        Boolean exists = redis.hasKey(key);
        if (Boolean.TRUE.equals(exists)) {
            Long ttl = redis.getExpire(key);
            long retryAfter = ttl != null && ttl > 0 ? ttl : rateLimitInterval;
            throw new BusinessException(ErrorCode.RATE_LIMITED,
                    "搜索过于频繁，请" + retryAfter + "秒后再试");
        }
        redis.opsForValue().set(key, "1", Duration.ofSeconds(rateLimitInterval));
    }

    private void logSearch(String keyword, String scope, String boardId,
                           long resultCount, String ipAddress, String userId) {
        try {
            SearchLog log = new SearchLog();
            log.setKeyword(keyword);
            log.setScope(scope);
            log.setBoardId(boardId);
            log.setResultCount((int) resultCount);
            log.setIpAddress(ipAddress);
            log.setUserId(userId);
            searchLogRepo.save(log);
        } catch (Exception e) {
            log.warn("Failed to save search log: {}", e.getMessage());
        }
    }

    private void updateHotTerms(String keyword) {
        try {
            String key = "search:hot:" + LocalDate.now();
            redis.opsForZSet().incrementScore(key, keyword, 1);
            redis.expire(key, Duration.ofDays(7));
        } catch (Exception e) {
            log.warn("Failed to update hot terms: {}", e.getMessage());
        }
    }
}
