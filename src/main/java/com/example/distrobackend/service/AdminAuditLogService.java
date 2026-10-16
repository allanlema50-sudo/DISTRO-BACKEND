
package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.AuditLog;
import com.example.distrobackend.dto.AdminAuditLogResponse;
import com.example.distrobackend.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuditLogService {

    private final AuditLogRepository auditLogRepository;

    @Transactional(readOnly = true)
    public Page<AdminAuditLogResponse> getAuditLogs(
            int page, int size, String search) {

        PageRequest pageable = PageRequest.of(
            Math.max(page, 0),
            Math.min(Math.max(size, 1), 100)
        );

        return auditLogRepository.searchAuditLogs(
                search == null ? "" : search.trim(),
                pageable
        ).map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public AdminAuditLogResponse getAuditLog(Long id) {
        AuditLog log = auditLogRepository.findById(id)
            .orElseThrow(() ->
                new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND,
                    "Audit log not found"
                ));

        return toResponse(log);
    }

    private AdminAuditLogResponse toResponse(AuditLog log) {
        String userId = null;
        String userName = null;
        String userEmail = null;

        if (log.getActor() != null) {
            userId = log.getActor().getId() == null
                ? null : log.getActor().getId().toString();
            userName = log.getActor().getFullName();
            userEmail = log.getActor().getEmail();
        }

        String description = log.getAction() + " "
            + log.getEntityType()
            + (log.getEntityId() == null
                ? "" : " " + log.getEntityId());

        String status = "SUCCESS";
        if (log.getMetadata() != null
                && log.getMetadata().get("status") != null) {
            status = log.getMetadata().get("status").toString();
        }

        return new AdminAuditLogResponse(
            log.getId(),
            userId,
            userName,
            userEmail,
            log.getAction(),
            log.getEntityType(),
            log.getEntityId() == null
                ? null : log.getEntityId().toString(),
            description,
            log.getIpAddress(),
            status,
            log.getCreatedAt(),
            log.getMetadata()
        );
    }
}
