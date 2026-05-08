package com.vibedev.service;

import com.vibedev.common.BusinessException;
import com.vibedev.dto.search.SearchResponse;
import com.vibedev.dto.search.SearchResultItem;
import com.vibedev.dto.search.SearchSuggestResponse;
import com.vibedev.dto.search.SearchSuggestionResponse;
import com.vibedev.dto.user.UserSummary;
import com.vibedev.entity.SearchLog;
import com.vibedev.repository.SearchLogRepository;
import com.vibedev.search.SearchEngine;
import com.vibedev.search.SearchQuery;
import com.vibedev.search.SearchScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchServiceTest {

    @Mock SearchEngine searchEngine;
    @Mock SearchLogRepository searchLogRepo;
    @Mock StringRedisTemplate redis;
    @Mock JdbcTemplate jdbc;
    @Mock ValueOperations<String, String> valueOps;
    @Mock ZSetOperations<String, String> zSetOps;

    SearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchService(searchEngine, searchLogRepo, redis, jdbc);
        ReflectionTestUtils.setField(searchService, "rateLimitInterval", 3);
        when(redis.opsForValue()).thenReturn(valueOps);
        when(redis.opsForZSet()).thenReturn(zSetOps);
    }

    @Test
    void shouldThrowWhenKeywordIsNull() {
        SearchQuery query = new SearchQuery(null, SearchScope.ALL, null, 1, 20);
        assertThrows(BusinessException.class, () ->
                searchService.search(query, "127.0.0.1", null));
    }

    @Test
    void shouldThrowWhenKeywordIsEmpty() {
        SearchQuery query = new SearchQuery("  ", SearchScope.ALL, null, 1, 20);
        assertThrows(BusinessException.class, () ->
                searchService.search(query, "127.0.0.1", null));
    }

    @Test
    void shouldThrowWhenKeywordTooShort() {
        SearchQuery query = new SearchQuery("a", SearchScope.ALL, null, 1, 20);
        var ex = assertThrows(BusinessException.class, () ->
                searchService.search(query, "127.0.0.1", null));
        assertTrue(ex.getMessage().contains("至少2个字符"));
    }

    @Test
    void shouldThrowWhenKeywordTooLong() {
        String longKeyword = "a".repeat(51);
        SearchQuery query = new SearchQuery(longKeyword, SearchScope.ALL, null, 1, 20);
        var ex = assertThrows(BusinessException.class, () ->
                searchService.search(query, "127.0.0.1", null));
        assertTrue(ex.getMessage().contains("最多50个字符"));
    }

    @Test
    void shouldThrowWhenBoardScopeWithoutBoardId() {
        SearchQuery query = new SearchQuery("test", SearchScope.BOARD, null, 1, 20);
        var ex = assertThrows(BusinessException.class, () ->
                searchService.search(query, "127.0.0.1", null));
        assertTrue(ex.getMessage().contains("board_id"));
    }

    @Test
    void shouldRateLimitByIp() {
        when(redis.hasKey("search:rate:192.168.1.1")).thenReturn(true);
        when(redis.getExpire("search:rate:192.168.1.1")).thenReturn(2L);

        SearchQuery query = new SearchQuery("test", SearchScope.ALL, null, 1, 20);
        var ex = assertThrows(BusinessException.class, () ->
                searchService.search(query, "192.168.1.1", null));
        assertTrue(ex.getMessage().contains("搜索过于频繁"));
    }

    @Test
    void shouldSetRateLimitOnFirstSearch() {
        when(redis.hasKey("search:rate:10.0.0.1")).thenReturn(false);

        SearchResultItem item = createTestItem("p1", "Test Post");
        SearchResponse engineResult = new SearchResponse(
                List.of(item), 1, 1, 20, "0.010s", null);
        when(searchEngine.search(any())).thenReturn(engineResult);

        SearchQuery query = new SearchQuery("test", SearchScope.ALL, null, 1, 20);
        SearchResponse result = searchService.search(query, "10.0.0.1", null);

        assertEquals(1, result.total());
        verify(redis).opsForValue();
        verify(valueOps).set(eq("search:rate:10.0.0.1"), eq("1"), eq(Duration.ofSeconds(3)));
    }

    @Test
    void shouldSearchSuccessfully() {
        when(redis.hasKey(anyString())).thenReturn(false);

        SearchResultItem item = createTestItem("p1", "Test Post Title");
        SearchResponse engineResult = new SearchResponse(
                List.of(item), 1, 1, 20, "0.005s", null);
        when(searchEngine.search(any())).thenReturn(engineResult);
        when(zSetOps.incrementScore(anyString(), anyString(), eq(1.0))).thenReturn(1.0);
        when(redis.expire(anyString(), any())).thenReturn(true);

        SearchQuery query = new SearchQuery("Test", SearchScope.ALL, null, 1, 20);
        SearchResponse result = searchService.search(query, "127.0.0.1", "user1");

        assertEquals(1, result.total());
        assertEquals(1, result.items().size());
        assertEquals("Test Post Title", result.items().get(0).title());

        // Verify search log was saved
        ArgumentCaptor<SearchLog> logCaptor = ArgumentCaptor.forClass(SearchLog.class);
        verify(searchLogRepo).save(logCaptor.capture());
        assertEquals("Test", logCaptor.getValue().getKeyword());
        assertEquals("127.0.0.1", logCaptor.getValue().getIpAddress());
    }

    @Test
    void shouldReturnSuggestionWhenNoResults() {
        when(redis.hasKey(anyString())).thenReturn(false);

        SearchResponse engineResult = new SearchResponse(
                List.of(), 0, 1, 20, "0.003s", null);
        when(searchEngine.search(any())).thenReturn(engineResult);

        SearchQuery query = new SearchQuery("nonexistent", SearchScope.ALL, null, 1, 20);
        SearchResponse result = searchService.search(query, "127.0.0.1", null);

        assertEquals(0, result.total());
        assertEquals("换个关键词试试", result.suggestion());
    }

    @Test
    void shouldGetSuggestions() {
        Set<String> hotSet = new LinkedHashSet<>();
        hotSet.add("React");
        hotSet.add("Go");
        when(zSetOps.reverseRange(contains("search:hot:"), eq(0L), eq(9L))).thenReturn(hotSet);

        SearchSuggestionResponse result = searchService.getSuggestions();

        assertEquals(2, result.hotSearches().size());
        assertTrue(result.hotSearches().contains("React"));
        assertTrue(result.hotSearches().contains("Go"));
    }

    @Test
    void shouldReturnEmptySuggestionsWhenNoHotTerms() {
        when(zSetOps.reverseRange(anyString(), eq(0L), eq(9L))).thenReturn(null);

        SearchSuggestionResponse result = searchService.getSuggestions();

        assertTrue(result.hotSearches().isEmpty());
    }

    @Test
    void shouldAcceptBoardScopeWithBoardId() {
        when(redis.hasKey(anyString())).thenReturn(false);

        SearchResultItem item = createTestItem("p1", "Test");
        SearchResponse engineResult = new SearchResponse(
                List.of(item), 1, 1, 20, "0.010s", null);
        when(searchEngine.search(any())).thenReturn(engineResult);

        SearchQuery query = new SearchQuery("test", SearchScope.BOARD, "board1", 1, 20);
        SearchResponse result = searchService.search(query, "127.0.0.1", null);

        assertNotNull(result);
        verify(searchEngine).search(argThat(q ->
                q.scope() == SearchScope.BOARD && "board1".equals(q.boardId())));
    }

    @Test
    void shouldClampLimitTo50() {
        SearchQuery query = new SearchQuery("test", SearchScope.ALL, null, 1, 100);
        assertEquals(50, query.limit());
    }

    @Test
    void shouldDefaultPageTo1() {
        SearchQuery query = new SearchQuery("test", SearchScope.ALL, null, 0, 20);
        assertEquals(1, query.page());
    }

    // --- getSuggest tests ---

    @Test
    void shouldReturnSuggestionsForPrefix() {
        when(jdbc.queryForList(
                contains("SELECT title"),
                eq(String.class),
                eq("React%"))
        ).thenReturn(List.of("React 18 新特性解析", "React Hooks 指南"));

        SearchSuggestResponse result = searchService.getSuggest("React");

        assertEquals(2, result.suggestions().size());
        assertTrue(result.suggestions().contains("React 18 新特性解析"));
        assertTrue(result.suggestions().contains("React Hooks 指南"));
    }

    @Test
    void shouldReturnEmptyForBlankPrefix() {
        SearchSuggestResponse result = searchService.getSuggest("  ");
        assertTrue(result.suggestions().isEmpty());
    }

    @Test
    void shouldReturnEmptyForOverlyLongPrefix() {
        String longPrefix = "a".repeat(51);
        SearchSuggestResponse result = searchService.getSuggest(longPrefix);
        assertTrue(result.suggestions().isEmpty());
    }

    @Test
    void shouldReturnEmptyWhenNoMatches() {
        when(jdbc.queryForList(
                contains("SELECT title"),
                eq(String.class),
                eq("XYZ%"))
        ).thenReturn(List.of());

        SearchSuggestResponse result = searchService.getSuggest("XYZ");
        assertTrue(result.suggestions().isEmpty());
    }

    @Test
    void shouldEscapeSpecialCharsInPrefix() {
        when(jdbc.queryForList(
                contains("SELECT title"),
                eq(String.class),
                eq("test%"))
        ).thenReturn(List.of("test post"));

        SearchSuggestResponse result = searchService.getSuggest("test");
        assertEquals(1, result.suggestions().size());
        verify(jdbc).queryForList(anyString(), eq(String.class), eq("test%"));
    }

    private SearchResultItem createTestItem(String id, String title) {
        UserSummary author = new UserSummary("u1", "testuser", "Test User",
                "/avatar.png", 1, null);
        return new SearchResultItem(id, title, title,
                "Test content excerpt", "Test Board", "b1",
                author, List.of(), 5, 3, 2, false, Instant.now());
    }
}
