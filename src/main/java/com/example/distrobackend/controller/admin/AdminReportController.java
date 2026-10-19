package com.example.distrobackend.controller.admin;

import com.example.distrobackend.dto.OrganizationAnalyticsResponse;
import com.example.distrobackend.dto.ReportSummaryResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;

    @GetMapping("/reports/overview")
    public ReportSummaryResponse overview(@AuthenticationPrincipal AuthenticatedUser actor) {
        return reportService.overview(actor);
    }

    @GetMapping("/reports/revenue")
    public ReportSummaryResponse revenue(@AuthenticationPrincipal AuthenticatedUser actor) {
        return reportService.revenue(actor);
    }

    @GetMapping("/reports/orders")
    public ReportSummaryResponse orders(@AuthenticationPrincipal AuthenticatedUser actor) {
        return reportService.orders(actor);
    }

    @GetMapping("/reports/payments")
    public ReportSummaryResponse payments(@AuthenticationPrincipal AuthenticatedUser actor) {
        return reportService.payments(actor);
    }

    @GetMapping("/reports/deliveries")
    public ReportSummaryResponse deliveries(@AuthenticationPrincipal AuthenticatedUser actor) {
        return reportService.deliveries(actor);
    }

    @GetMapping("/reports/inventory")
    public ReportSummaryResponse inventory(@AuthenticationPrincipal AuthenticatedUser actor) {
        return reportService.inventory(actor);
    }

    @GetMapping("/analytics")
    public ReportSummaryResponse analytics(@AuthenticationPrincipal AuthenticatedUser actor) {
        return reportService.overview(actor);
    }

    @GetMapping("/analytics/organizations")
    public Page<OrganizationAnalyticsResponse> organizationAnalytics(
            @AuthenticationPrincipal AuthenticatedUser actor, Pageable pageable) {
        return reportService.organizations(actor, pageable);
    }
}
