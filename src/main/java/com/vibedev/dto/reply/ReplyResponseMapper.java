package com.vibedev.dto.reply;

import com.vibedev.dto.user.UserSummary;
import com.vibedev.entity.Reply;
import com.vibedev.entity.User;

import java.util.Collections;
import java.util.List;

public final class ReplyResponseMapper {

    private ReplyResponseMapper() {}

    public static ReplyResponse from(Reply r, User author, boolean isLikedByCurrentUser) {
        return from(r, author, isLikedByCurrentUser, Collections.emptyList());
    }

    public static ReplyResponse from(Reply r, User author, boolean isLikedByCurrentUser,
                                     List<ReplyResponse> childReplies) {
        var authorDto = author != null
                ? UserSummary.from(author)
                : new UserSummary("unknown", "unknown", "未知用户", "", 1, "user");
        return new ReplyResponse(
                r.getId(),
                r.isDeleted() ? "该回复已被删除" : r.getContentMarkdown(),
                r.isDeleted() ? "<p>该回复已被删除</p>" : r.getContentHtml(),
                r.getAuthorId(),
                r.getPostId(),
                r.getParentReplyId(),
                r.getDepth(),
                authorDto,
                isLikedByCurrentUser,
                r.getLikeCount(),
                r.getAuditStatus(),
                r.isDeleted(),
                r.getVersion(),
                r.getCreatedAt(),
                r.getUpdatedAt(),
                r.getLastEditedAt(),
                childReplies
        );
    }
}
