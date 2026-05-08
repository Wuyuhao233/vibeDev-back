package com.vibedev.search;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SearchUtils {

    private SearchUtils() {}

    static String escapeHtml(String text) {
        if (text == null) return "";
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    static String highlight(String text, String keyword, String preTag, String postTag) {
        if (text == null || keyword == null || keyword.isEmpty()) return escapeHtml(text);
        String escaped = escapeHtml(text);
        String escapedKeyword = Pattern.quote(escapeHtml(keyword));
        Pattern pattern = Pattern.compile(escapedKeyword, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(escaped);
        return matcher.replaceAll(preTag + "$0" + postTag);
    }

    static String excerpt(String text, String keyword, int maxLength, String preTag, String postTag) {
        if (text == null) return "";
        if (keyword == null || keyword.isEmpty()) {
            String escaped = escapeHtml(text);
            return escaped.length() <= maxLength ? escaped : escaped.substring(0, maxLength) + "...";
        }
        String escaped = escapeHtml(text);
        String escapedKeyword = Pattern.quote(escapeHtml(keyword));
        Pattern pattern = Pattern.compile(escapedKeyword, Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(escaped);

        if (!matcher.find()) {
            return escaped.length() <= maxLength ? escaped : escaped.substring(0, maxLength) + "...";
        }

        int matchStart = matcher.start();
        int half = maxLength / 2;
        int start = Math.max(0, matchStart - half);
        int end = Math.min(escaped.length(), matchStart + matcher.group().length() + half);

        // Adjust start to not break mid-character (multi-byte Unicode)
        while (start > 0 && Character.isLowSurrogate(escaped.charAt(start))) start--;
        while (end < escaped.length() && Character.isLowSurrogate(escaped.charAt(end))) end++;

        String excerpt = escaped.substring(start, end);
        // Re-apply highlighting on the excerpt
        excerpt = highlight(excerpt, keyword, preTag, postTag);

        StringBuilder sb = new StringBuilder();
        if (start > 0) sb.append("...");
        sb.append(excerpt);
        if (end < escaped.length()) sb.append("...");
        return sb.toString();
    }

    static String escapeLikeWildcards(String keyword) {
        if (keyword == null) return "";
        return keyword
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    static String escapeFtsSpecialChars(String keyword) {
        if (keyword == null) return "";
        return keyword
                .replace("+", "\\+")
                .replace("-", "\\-")
                .replace(">", "\\>")
                .replace("<", "\\<")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("~", "\\~")
                .replace("*", "\\*")
                .replace("\"", "\\\"")
                .replace("@", "\\@");
    }
}
