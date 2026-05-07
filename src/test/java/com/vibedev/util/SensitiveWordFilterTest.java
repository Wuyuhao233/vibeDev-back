package com.vibedev.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SensitiveWordFilterTest {

    @Test
    void shouldFindSingleMatch() {
        var filter = new SensitiveWordFilter();
        filter.rebuild(List.of("敏感词", "测试"));

        var matches = filter.findMatches("这是一个敏感词的测试");

        assertEquals(2, matches.size());
        assertTrue(matches.stream().anyMatch(m -> m.word().equals("敏感词")));
        assertTrue(matches.stream().anyMatch(m -> m.word().equals("测试")));
    }

    @Test
    void shouldReturnEmptyForCleanContent() {
        var filter = new SensitiveWordFilter();
        filter.rebuild(List.of("敏感词"));

        var matches = filter.findMatches("这是正常内容");

        assertTrue(matches.isEmpty());
    }

    @Test
    void shouldFindOverlappingMatches() {
        var filter = new SensitiveWordFilter();
        filter.rebuild(List.of("ab", "bc", "abc"));

        var matches = filter.findMatches("abc");

        // Should match "ab" at 0, "abc" at 0, "bc" at 1
        assertTrue(matches.size() >= 3);
    }

    @Test
    void shouldReturnCorrectPositions() {
        var filter = new SensitiveWordFilter();
        filter.rebuild(List.of("hello"));

        var matches = filter.findMatches("say hello world");

        assertEquals(1, matches.size());
        assertEquals("hello", matches.get(0).word());
        assertEquals(4, matches.get(0).position());
    }

    @Test
    void shouldHandleNullText() {
        var filter = new SensitiveWordFilter();
        filter.rebuild(List.of("test"));

        assertTrue(filter.findMatches(null).isEmpty());
    }

    @Test
    void shouldHandleEmptyText() {
        var filter = new SensitiveWordFilter();
        filter.rebuild(List.of("test"));

        assertTrue(filter.findMatches("").isEmpty());
    }

    @Test
    void shouldHandleEmptyWordList() {
        var filter = new SensitiveWordFilter();
        filter.rebuild(List.of());

        var matches = filter.findMatches("test content");
        assertTrue(matches.isEmpty());
    }

    @Test
    void shouldFindMultipleOccurrences() {
        var filter = new SensitiveWordFilter();
        filter.rebuild(List.of("bad"));

        var matches = filter.findMatches("bad word and bad thing");

        assertEquals(2, matches.size());
        assertEquals(0, matches.get(0).position());
        assertEquals(13, matches.get(1).position());
    }

    @Test
    void hasSensitiveShouldReturnCorrectly() {
        var filter = new SensitiveWordFilter();
        filter.rebuild(List.of("bad"));

        assertTrue(filter.hasSensitive("this is bad"));
        assertFalse(filter.hasSensitive("this is good"));
    }
}
