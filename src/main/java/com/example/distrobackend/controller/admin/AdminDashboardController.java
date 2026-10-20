package com.example.distrobackend.controller.admin;

import com.example.distrobackend.dto.AdminDashboardResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    @GetMapping
    public AdminDashboardResponse get(@AuthenticationPrincipal AuthenticatedUser actor) {
        return dashboardService.get(actor);
    }
}
