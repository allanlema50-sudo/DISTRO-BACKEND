package com.example.distrobackend.controller;

import com.example.distrobackend.dto.PurchaseOrderResponse;
import com.example.distrobackend.dto.PurchaseOrderSettlementResponse;
import com.example.distrobackend.dto.PickPurchaseOrderItemRequest;
import com.example.distrobackend.dto.PackPurchaseOrderRequest;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.BankTransferSettlementService;
import com.example.distrobackend.service.PurchaseOrderService;
import com.example.distrobackend.service.SettlementProofDocumentService;
import com.example.distrobackend.dto.ConfirmBankPaymentRequest;
import com.example.distrobackend.dto.PurchaseOrderDecisionRequest;
import com.example.distrobackend.service.CentralizedCheckoutService;
import com.example.distrobackend.service.PurchaseOrderShipmentService;
import com.example.distrobackend.dto.SupplierFinancialStatusResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/manufacturer/purchase-orders")
@PreAuthorize("hasAnyRole('MANUFACTURER_ADMIN','MANUFACTURER_STAFF')")
@RequiredArgsConstructor
public class ManufacturerPurchaseOrderController {

    private final PurchaseOrderService purchaseOrderService;
    private final CentralizedCheckoutService centralizedCheckoutService;
    private final PurchaseOrderShipmentService purchaseOrderShipmentService;
    private final BankTransferSettlementService bankTransferSettlementService;
    private final SettlementProofDocumentService proofService;

    @GetMapping
    public Page<PurchaseOrderResponse> list(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PageableDefault(size = 20) Pageable pageable) {
        return purchaseOrderService.listForManufacturer(actor, pageable);
    }

    @GetMapping("/{purchaseOrderId}")
    public PurchaseOrderResponse get(@AuthenticationPrincipal AuthenticatedUser actor,
                                     @PathVariable UUID purchaseOrderId) {
        return purchaseOrderService.getForManufacturer(actor, purchaseOrderId);
    }

    @PatchMapping("/{purchaseOrderId}/warehouse/pick")
    public PurchaseOrderResponse pick(@AuthenticationPrincipal AuthenticatedUser actor,
                                      @PathVariable UUID purchaseOrderId,
                                      @Valid @RequestBody PickPurchaseOrderItemRequest request) {
        return purchaseOrderService.pickManufacturerItem(actor, purchaseOrderId, request);
    }

    @PatchMapping("/{purchaseOrderId}/warehouse/pack")
    public PurchaseOrderResponse pack(@AuthenticationPrincipal AuthenticatedUser actor,
                                      @PathVariable UUID purchaseOrderId,
                                      @Valid @RequestBody PackPurchaseOrderRequest request) {
        return purchaseOrderService.packManufacturerOrder(actor, purchaseOrderId, request);
    }

    @PostMapping("/{purchaseOrderId}/fulfilment/retry")
    public PurchaseOrderResponse retryFulfilment(@AuthenticationPrincipal AuthenticatedUser actor,
                                                  @PathVariable UUID purchaseOrderId) {
        purchaseOrderShipmentService.retryCheckoutFulfilment(actor, purchaseOrderId);
        return purchaseOrderService.getForManufacturer(actor, purchaseOrderId);
    }

    @PatchMapping("/{purchaseOrderId}/decision")
    @PreAuthorize("hasRole('MANUFACTURER_ADMIN')")
    public PurchaseOrderResponse decide(@AuthenticationPrincipal AuthenticatedUser actor,
                                        @PathVariable UUID purchaseOrderId,
                                        @Valid @RequestBody PurchaseOrderDecisionRequest request) {
        return purchaseOrderService.decide(actor, purchaseOrderId, request);
    }

    @GetMapping("/{purchaseOrderId}/financial-status")
    public SupplierFinancialStatusResponse financialStatus(@AuthenticationPrincipal AuthenticatedUser actor,
                                                            @PathVariable UUID purchaseOrderId) {
        return centralizedCheckoutService.supplierFinancialStatus(actor, purchaseOrderId);
    }
    @GetMapping("/{purchaseOrderId}/settlements")
    public List<PurchaseOrderSettlementResponse> settlements(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID purchaseOrderId) {
        return purchaseOrderService.settlements(actor, purchaseOrderId);
    }

    /** Records a bank payment the supplier has seen arrive. Supplier administrators only. */
    @PostMapping("/{purchaseOrderId}/settlement/confirm")
    @PreAuthorize("hasRole('MANUFACTURER_ADMIN')")
    public PurchaseOrderResponse confirmBankPayment(@AuthenticationPrincipal AuthenticatedUser actor,
                                                    @PathVariable UUID purchaseOrderId,
                                                    @Valid @RequestBody ConfirmBankPaymentRequest request) {
        bankTransferSettlementService.confirm(actor, purchaseOrderId, request);
        return purchaseOrderService.getForManufacturer(actor, purchaseOrderId);
    }

    @GetMapping("/{purchaseOrderId}/settlement/proofs/{documentId}")
    public ResponseEntity<byte[]> proof(@AuthenticationPrincipal AuthenticatedUser actor,
                                        @PathVariable UUID purchaseOrderId,
                                        @PathVariable UUID documentId) {
        return ProofDownloadResponses.of(proofService.download(actor, purchaseOrderId, documentId));
    }}
