package com.vibedev.service;

import com.vibedev.entity.AuditLog;
import com.vibedev.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);
    private final AuditLogRepository auditLogRepo;

    public AuditLogService(AuditLogRepository auditLogRepo) {
        this.auditLogRepo = auditLogRepo;
    }

    public void log(String actorId, String actorUsername, String action,
                    String targetType, String targetId, String detail, String ipAddress) {
        var entry = new AuditLog();
        entry.setId(UUID.randomUUID().toString());
        entry.setActorId(actorId);
        entry.setActorUsername(actorUsername);
        entry.setAction(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setDetail(detail);
        entry.setIpAddress(ipAddress);
        auditLogRepo.save(entry);
    }
}
