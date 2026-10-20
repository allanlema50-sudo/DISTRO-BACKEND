package com.example.distrobackend.service;

import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.AuditLogResponse;
import com.example.distrobackend.repository.AuditLogRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> search(AuthenticatedUser actor, UUID actorId,
                                         String entityType, UUID entityId, Pageable pageable) {
        requirePlatformAdmin(actor);
        String normalizedType = entityType == null || entityType.isBlank() ? null : entityType.trim();
        return auditLogRepository.search(actorId, normalizedType, entityId, pageable)
                .map(AuditLogResponse::from);
    }

    @Transactional(readOnly = true)
    public AuditLogResponse get(AuthenticatedUser actor, Long auditLogId) {
        requirePlatformAdmin(actor);
        return auditLogRepository.findByIdWithActor(auditLogId)
                .map(AuditLogResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Audit log not found"));
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> byUser(AuthenticatedUser actor, UUID userId, Pageable pageable) {
        requirePlatformAdmin(actor);
        return auditLogRepository.search(userId, null, null, pageable).map(AuditLogResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<AuditLogResponse> byOrganization(AuthenticatedUser actor, UUID organizationId,
                                                 Pageable pageable) {
        requirePlatformAdmin(actor);
        return auditLogRepository.findByOrganizationId(organizationId, pageable).map(AuditLogResponse::from);
    }

    private static void requirePlatformAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }
}
