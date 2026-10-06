package com.example.distrobackend.controller.admin;

import com.example.distrobackend.dto.AdminStockResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.AdminStockService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/stock")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN','SUPER_ADMIN','MANUFACTURER_ADMIN','DISTRIBUTOR_ADMIN')")
public class AdminStockController {

    private final AdminStockService adminStockService;

    @GetMapping
    public List<AdminStockResponse> getAllStock(
            @AuthenticationPrincipal AuthenticatedUser me
    ) {
        return adminStockService.getAllStock(me);
    }
}
