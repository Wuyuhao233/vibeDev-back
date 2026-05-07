package com.vibedev.dto.board;

import java.time.Instant;
import java.util.List;

public record TagItem(String id, String name, String boardId, int sortOrder, int postCount) {
}
