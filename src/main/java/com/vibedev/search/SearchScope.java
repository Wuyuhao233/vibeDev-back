package com.vibedev.search;

public enum SearchScope {
    ALL,
    BOARD,
    TITLE_ONLY,
    TITLE_CONTENT;

    public static SearchScope from(String s) {
        if (s == null) return ALL;
        return switch (s.toLowerCase()) {
            case "board" -> BOARD;
            case "title_only" -> TITLE_ONLY;
            case "title_content" -> TITLE_CONTENT;
            default -> ALL;
        };
    }
}
