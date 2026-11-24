package com.example.distrobackend.controller;

import com.example.distrobackend.dto.CreatePurchaseOrderRequest;
import com.example.distrobackend.dto.PurchaseOrderResponse;
import com.example.distrobackend.dto.PurchaseOrderSettlementResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.BankTransferSettlementService;
import com.example.distrobackend.service.PurchaseOrderService;
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
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/purchase-orders")
@PreAuthorize("hasAnyRole('DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF')")
@RequiredArgsConstructor
public class PurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final BankTransferSettlementService bankTransferSettlementService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrderResponse create(@AuthenticationPrincipal AuthenticatedUser actor,
                                         @Valid @RequestBody CreatePurchaseOrderRequest request) {
        return purchaseOrderService.create(actor, request);
    }

    @GetMapping
    public Page<PurchaseOrderResponse> list(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PageableDefault(size = 20) Pageable pageable) {
        return purchaseOrderService.listForDistributor(actor, pageable);
    }

    @GetMapping("/{purchaseOrderId}")
    public PurchaseOrderResponse get(@AuthenticationPrincipal AuthenticatedUser actor,
                                     @PathVariable UUID purchaseOrderId) {
        return purchaseOrderService.getForDistributor(actor, purchaseOrderId);
    }

    @GetMapping("/{purchaseOrderId}/settlements")
    public List<PurchaseOrderSettlementResponse> settlements(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID purchaseOrderId) {
        return purchaseOrderService.settlements(actor, purchaseOrderId);
    }

    /**
     * Submits a bank reference and proof for a purchase order. The order stays
     * unpaid until the supplier's administrator confirms the amount received.
     */
    @PostMapping(value = "/{purchaseOrderId}/settlement/bank-transfer",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public PurchaseOrderResponse submitBankTransfer(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID purchaseOrderId,
            @RequestParam("reference") String reference,
            @RequestParam("file") MultipartFile file,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        bankTransferSettlementService.submit(actor, purchaseOrderId, reference, file, idempotencyKey);
        return purchaseOrderService.getForDistributor(actor, purchaseOrderId);
    }}
