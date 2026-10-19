package com.example.distrobackend.controller.admin;

import com.example.distrobackend.dto.AuditLogResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminAuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping
    public Page<AuditLogResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                       @RequestParam(required = false) UUID userId,
                                       @RequestParam(required = false) String entityType,
                                       @RequestParam(required = false) UUID entityId,
                                       Pageable pageable) {
        return auditLogService.search(actor, userId, entityType, entityId, pageable);
    }

    @GetMapping("/{auditLogId}")
    public AuditLogResponse get(@AuthenticationPrincipal AuthenticatedUser actor,
                                @PathVariable Long auditLogId) {
        return auditLogService.get(actor, auditLogId);
    }

    @GetMapping("/user/{userId}")
    public Page<AuditLogResponse> byUser(@AuthenticationPrincipal AuthenticatedUser actor,
                                         @PathVariable UUID userId, Pageable pageable) {
        return auditLogService.byUser(actor, userId, pageable);
    }

    @GetMapping("/organization/{organizationId}")
    public Page<AuditLogResponse> byOrganization(@AuthenticationPrincipal AuthenticatedUser actor,
                                                 @PathVariable UUID organizationId, Pageable pageable) {
        return auditLogService.byOrganization(actor, organizationId, pageable);
    }

    @GetMapping("/resource")
    public Page<AuditLogResponse> byResource(@AuthenticationPrincipal AuthenticatedUser actor,
                                             @RequestParam String entityType,
                                             @RequestParam UUID entityId,
                                             Pageable pageable) {
        return auditLogService.search(actor, null, entityType, entityId, pageable);
    }
}
