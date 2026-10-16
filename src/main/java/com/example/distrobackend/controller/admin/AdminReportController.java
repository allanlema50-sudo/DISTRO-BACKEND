
package com.example.distrobackend.controller.admin;

import com.example.distrobackend.dto.AdminReportOverviewResponse;
import com.example.distrobackend.service.AdminReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.example.distrobackend.dto.MonthlyOrderPoint;
import com.example.distrobackend.dto.MonthlyRevenuePoint;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'SUPER_ADMIN')")
public class AdminReportController {

    private final AdminReportService adminReportService;

    @GetMapping("/overview")
    public AdminReportOverviewResponse getOverview() {
        return adminReportService.getOverview();
    }

    @GetMapping("/orders")
public List<MonthlyOrderPoint> getOrderReport(
        @RequestParam(defaultValue = "12_MONTHS") String period) {
    return adminReportService.getOrderReport(period);
}

@GetMapping("/revenue")
public List<MonthlyRevenuePoint> getRevenueReport(
        @RequestParam(defaultValue = "12_MONTHS") String period) {
    return adminReportService.getRevenueReport(period);
}
}
