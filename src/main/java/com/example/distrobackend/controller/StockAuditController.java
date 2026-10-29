package com.example.distrobackend.controller;

import com.example.distrobackend.dto.CreateStockAuditRequest;
import com.example.distrobackend.dto.StockAuditResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.StockAuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/stock-audits")
@PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF','DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF')")
@RequiredArgsConstructor
public class StockAuditController {

    private final StockAuditService stockAuditService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockAuditResponse record(@AuthenticationPrincipal AuthenticatedUser actor,
                                     @Valid @RequestBody CreateStockAuditRequest request) {
        return stockAuditService.record(actor, request);
    }

    @GetMapping
    public Page<StockAuditResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                         @PageableDefault(size = 20) Pageable pageable) {
        return stockAuditService.list(actor, pageable);
    }

    @GetMapping("/{auditId}")
    public StockAuditResponse get(@AuthenticationPrincipal AuthenticatedUser actor,
                                  @PathVariable UUID auditId) {
        return stockAuditService.get(actor, auditId);
    }

    @PatchMapping("/{auditId}/apply")
    @PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','DISTRIBUTOR_ADMIN')")
    public StockAuditResponse apply(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @PathVariable UUID auditId) {
        return stockAuditService.apply(actor, auditId);
    }
}
