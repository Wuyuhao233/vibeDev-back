package com.vibedev.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SearchUtilsTest {

    @Test
    void shouldEscapeHtml() {
        assertEquals("&lt;script&gt;", SearchUtils.escapeHtml("<script>"));
        assertEquals("&amp;lt;&amp;gt;", SearchUtils.escapeHtml("&lt;&gt;"));
        assertEquals("&quot;test&quot;", SearchUtils.escapeHtml("\"test\""));
    }

    @Test
    void shouldEscapeNullToEmpty() {
        assertEquals("", SearchUtils.escapeHtml(null));
    }

    @Test
    void shouldHighlightKeyword() {
        String result = SearchUtils.highlight("Hello World", "World", "<mark>", "</mark>");
        assertEquals("Hello <mark>World</mark>", result);

        // Case insensitive
        result = SearchUtils.highlight("Hello world", "World", "<mark>", "</mark>");
        assertEquals("Hello <mark>world</mark>", result);
    }

    @Test
    void shouldHighlightAfterHtmlEscape() {
        // The input is already plain text; the highlight method escapes HTML first,
        // then highlights the keyword in the escaped text
        String result = SearchUtils.highlight("<p>Hello World</p>", "World", "<mark>", "</mark>");
        // First: HTML escaped -> "&lt;p&gt;Hello World&lt;/p&gt;"
        // Then: keyword highlighted
        assertEquals("&lt;p&gt;Hello <mark>World</mark>&lt;/p&gt;", result);
    }

    @Test
    void shouldCreateExcerptAroundKeyword() {
        String text = "a".repeat(80) + " MATCH_HERE " + "b".repeat(80);
        String result = SearchUtils.excerpt(text, "MATCH_HERE", 150, "<mark>", "</mark>");
        assertTrue(result.contains("<mark>MATCH_HERE</mark>"));
        assertTrue(result.startsWith("...") || result.endsWith("..."));
    }

    @Test
    void shouldCreateExcerptForShortText() {
        String result = SearchUtils.excerpt("Short text", "Short", 150, "<mark>", "</mark>");
        assertTrue(result.contains("<mark>Short</mark>"));
        assertFalse(result.contains("..."));
    }

    @Test
    void shouldReturnEmptyExcerptForNull() {
        assertEquals("", SearchUtils.excerpt(null, "test", 150, "<mark>", "</mark>"));
    }

    @Test
    void shouldReturnTruncatedExcerptWhenNoKeywordMatch() {
        String text = "x".repeat(200);
        String result = SearchUtils.excerpt(text, "NOMATCH", 150, "<mark>", "</mark>");
        assertTrue(result.length() <= 153); // 150 + "..."
    }

    @Test
    void shouldReturnEmptyExcerptWhenNoKeywordAndNull() {
        String result = SearchUtils.excerpt("Some text", null, 150, "<mark>", "</mark>");
        assertEquals("Some text", result);
    }

    @Test
    void shouldEscapeLikeWildcards() {
        assertEquals("test\\%test\\_test", SearchUtils.escapeLikeWildcards("test%test_test"));
        assertEquals("test\\\\test", SearchUtils.escapeLikeWildcards("test\\test"));
    }

    @Test
    void shouldEscapeFtsSpecialChars() {
        String result = SearchUtils.escapeFtsSpecialChars("test+-><()~*\"@");
        // After escaping, each special char should be prefixed with backslash
        assertTrue(result.contains("\\+"));
        assertTrue(result.contains("\\-"));
        assertTrue(result.contains("\\>"));
        assertTrue(result.contains("\\<"));
        assertTrue(result.contains("\\("));
        assertTrue(result.contains("\\)"));
        assertTrue(result.contains("\\~"));
        assertTrue(result.contains("\\*"));
        assertTrue(result.contains("\\\""));
        assertTrue(result.contains("\\@"));
    }

    @Test
    void shouldHandleNullInEscapeMethods() {
        assertEquals("", SearchUtils.escapeLikeWildcards(null));
        assertEquals("", SearchUtils.escapeFtsSpecialChars(null));
    }

    @Test
    void shouldHighlightChineseText() {
        String result = SearchUtils.highlight("欢迎使用搜索功能", "搜索", "<mark>", "</mark>");
        assertEquals("欢迎使用<mark>搜索</mark>功能", result);
    }

    @Test
    void shouldHighlightMultipleOccurrences() {
        String result = SearchUtils.highlight("test test test", "test", "<mark>", "</mark>");
        assertEquals("<mark>test</mark> <mark>test</mark> <mark>test</mark>", result);
    }
}
