package com.vibedev.dto.appeal;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AppealItem(
        String id,
        @JsonProperty("content_id") String contentId,
        @JsonProperty("content_title") String contentTitle,
        @JsonProperty("content_summary") String contentSummary,
        @JsonProperty("appellant_username") String appellantUsername,
        @JsonProperty("violation_category") String violationCategory,
        @JsonProperty("ai_score") Integer aiScore,
        @JsonProperty("appeal_reason") String appealReason,
        String status,
        @JsonProperty("reviewed_by") String reviewedBy,
        @JsonProperty("review_note") String reviewNote,
        @JsonProperty("created_at") Instant createdAt,
        @JsonProperty("reviewed_at") Instant reviewedAt
) {}
