package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.AuditLog;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
        Long id,
        UUID actorId,
        String action,
        String entityType,
        UUID entityId,
        Map<String, Object> metadata,
        String ipAddress,
        OffsetDateTime createdAt
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getActor() == null ? null : log.getActor().getId(),
                log.getAction(), log.getEntityType(), log.getEntityId(), log.getMetadata(),
                log.getIpAddress(), log.getCreatedAt());
    }
}
