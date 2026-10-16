
package com.example.distrobackend.controller.admin;

import com.example.distrobackend.dto.AdminAuditLogResponse;
import com.example.distrobackend.service.AdminAuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'SUPER_ADMIN')")
public class AdminAuditLogController {

    private final AdminAuditLogService adminAuditLogService;

    @GetMapping
    public Page<AdminAuditLogResponse> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "") String search) {

        return adminAuditLogService.getAuditLogs(page, size, search);
    }

    @GetMapping("/{id}")
    public AdminAuditLogResponse getAuditLog(@PathVariable Long id) {
        return adminAuditLogService.getAuditLog(id);
    }
}
