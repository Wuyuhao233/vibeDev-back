package com.vibedev.dto.search;

import com.vibedev.dto.user.UserSummary;
import com.vibedev.dto.board.TagItem;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.List;

public record SearchResultItem(
        String id,
        String title,
        String titleHighlighted,
        String contentExcerpt,
        String boardName,
        String boardId,
        UserSummary author,
        List<TagItem> tags,
        int likeCount,
        int replyCount,
        @JsonProperty("bookmarkCount") int collectCount,
        boolean isEssenced,
        Instant createdAt) {
}
