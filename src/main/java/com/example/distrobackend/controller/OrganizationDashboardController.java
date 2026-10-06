package com.example.distrobackend.controller;

import com.example.distrobackend.dto.OrganizationDashboardResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.OrganizationDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/organization/dashboard")
@RequiredArgsConstructor
@PreAuthorize("""
        hasAnyRole(
            'MANUFACTURER_ADMIN',
            'MANUFACTURER_STAFF',
            'DISTRIBUTOR_ADMIN',
            'DISTRIBUTOR_STAFF'
        )
        """)
public class OrganizationDashboardController {

    private final OrganizationDashboardService dashboardService;

    @GetMapping
    public OrganizationDashboardResponse getDashboard(
            @AuthenticationPrincipal AuthenticatedUser me
    ) {
        return dashboardService.getDashboard(me);
    }
}
