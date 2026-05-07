package com.vibedev.dto.appeal;

import com.vibedev.entity.Appeal;

import java.time.Instant;

public record AppealItem(
        String id,
        String reportId,
        String appellantId,
        String reason,
        String status,
        String handlerId,
        String handlerNote,
        Instant createdAt,
        Instant processedAt
) {
    public static AppealItem from(Appeal a) {
        return new AppealItem(
                a.getId(),
                a.getReportId(),
                a.getAppellantId(),
                a.getReason(),
                a.getStatus(),
                a.getHandlerId(),
                a.getHandlerNote(),
                a.getCreatedAt(),
                a.getProcessedAt()
        );
    }
}
