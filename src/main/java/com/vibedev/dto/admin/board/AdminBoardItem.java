package com.vibedev.dto.admin.board;

import com.vibedev.entity.Board;
import com.vibedev.entity.Tag;
import java.time.Instant;
import java.util.List;

public record AdminBoardItem(
        String id, String name, String icon, String description,
        int sortOrder, String status, int postCount, int tagCount,
        boolean isDeleted, Instant createdAt, Instant updatedAt,
        List<TagItem> tags
) {
    public record TagItem(String id, String name, int sortOrder, int postCount) {
        public static TagItem from(Tag t) {
            return new TagItem(t.getId(), t.getName(), t.getSortOrder(), t.getPostCount());
        }
    }

    public static AdminBoardItem from(Board b, List<Tag> tags) {
        return new AdminBoardItem(
                b.getId(), b.getName(), b.getIcon(), b.getDescription(),
                b.getSortOrder(), b.getStatus(), b.getPostCount(), tags.size(),
                b.isDeleted(), b.getCreatedAt(), b.getUpdatedAt(),
                tags.stream().map(TagItem::from).toList()
        );
    }
}
