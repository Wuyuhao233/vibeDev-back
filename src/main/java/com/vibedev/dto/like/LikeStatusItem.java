package com.vibedev.dto.like;

public record LikeStatusItem(String targetType, String targetId, boolean isLiked, int count) {}
