package com.example.distrobackend.controller.admin;

import com.example.distrobackend.dto.AdminCreateStockItemRequest;
import com.example.distrobackend.dto.StockAdjustmentRequest;
import com.example.distrobackend.dto.StockItemResponse;
import com.example.distrobackend.dto.StockMovementResponse;
import com.example.distrobackend.dto.UpdateStockItemRequest;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.AdminStockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/stock")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminStockController {

    private final AdminStockService stockService;

    @GetMapping
    public Page<StockItemResponse> list(@AuthenticationPrincipal AuthenticatedUser actor,
                                        @RequestParam(required = false) UUID organizationId,
                                        @RequestParam(required = false) Boolean active,
                                        Pageable pageable) {
        return stockService.list(actor, organizationId, active, pageable);
    }

    @GetMapping("/alerts")
    public Page<StockItemResponse> alerts(@AuthenticationPrincipal AuthenticatedUser actor,
                                          @RequestParam(required = false) UUID organizationId,
                                          Pageable pageable) {
        return stockService.alerts(actor, organizationId, pageable);
    }

    @GetMapping("/{inventoryId}")
    public StockItemResponse get(@AuthenticationPrincipal AuthenticatedUser actor,
                                 @PathVariable UUID inventoryId) {
        return stockService.get(actor, inventoryId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockItemResponse create(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @Valid @RequestBody AdminCreateStockItemRequest request) {
        return stockService.create(actor, request);
    }

    @PatchMapping("/{inventoryId}")
    public StockItemResponse update(@AuthenticationPrincipal AuthenticatedUser actor,
                                    @PathVariable UUID inventoryId,
                                    @Valid @RequestBody UpdateStockItemRequest request) {
        return stockService.update(actor, inventoryId, request);
    }

    @GetMapping("/{inventoryId}/movements")
    public Page<StockMovementResponse> movements(@AuthenticationPrincipal AuthenticatedUser actor,
                                                 @PathVariable UUID inventoryId,
                                                 Pageable pageable) {
        return stockService.movements(actor, inventoryId, pageable);
    }

    @PostMapping("/{inventoryId}/movements")
    public StockMovementResponse adjust(@AuthenticationPrincipal AuthenticatedUser actor,
                                        @PathVariable UUID inventoryId,
                                        @Valid @RequestBody StockAdjustmentRequest request) {
        return stockService.adjust(actor, inventoryId, request);
    }
}
