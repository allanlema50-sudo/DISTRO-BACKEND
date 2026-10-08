package com.example.distrobackend.controller;

import com.example.distrobackend.dto.OrganizationNotificationResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.OrganizationNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class OrganizationNotificationController {

    private final OrganizationNotificationService notificationService;

    @GetMapping
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF','DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF') and @tenantAccess.hasOrganization(authentication)")
    public Page<OrganizationNotificationResponse> list(
            @AuthenticationPrincipal AuthenticatedUser user, Pageable pageable) {
        return notificationService.list(user, pageable);
    }
}
