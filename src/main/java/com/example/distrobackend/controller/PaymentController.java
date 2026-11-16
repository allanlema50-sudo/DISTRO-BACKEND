package com.example.distrobackend.controller;

import com.example.distrobackend.dto.PaymentInitiateRequest;
import com.example.distrobackend.dto.PaymentInitiateResponse;
import com.example.distrobackend.dto.PaymentStatusResponse;
import com.example.distrobackend.dto.PaymentResponse;
import com.example.distrobackend.dto.mpesa.MpesaCallbackRequest;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.PaymentService;
import com.example.distrobackend.service.callbackhandling;
import com.example.distrobackend.service.PaymentAdministrationService;
import com.example.distrobackend.service.PurchaseOrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@RestController
@RequestMapping({"/api/v1/payments", "/api/payments"})
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final callbackhandling callbackHandling;
    private final PaymentAdministrationService paymentAdministrationService;
    private final com.example.distrobackend.service.PurchaseOrderService purchaseOrderService;

    @GetMapping
    public Object listPayments(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) String orderType,
            @RequestParam(required = false) UUID orderId,
            Pageable pageable) {
        if (orderType != null && "PURCHASE_ORDER".equalsIgnoreCase(orderType.trim())) {
            if (orderId == null) {
                throw new com.example.distrobackend.Exception.ApiException(
                        com.example.distrobackend.Exception.ErrorCode.BAD_REQUEST,
                        "orderId is required when orderType is PURCHASE_ORDER");
            }
            return purchaseOrderService.settlements(actor, orderId);
        }
        if (orderType != null && !"CUSTOMER_ORDER".equalsIgnoreCase(orderType.trim())) {
            throw new com.example.distrobackend.Exception.ApiException(
                    com.example.distrobackend.Exception.ErrorCode.BAD_REQUEST, "Unsupported payment orderType");
        }
        if (orderId != null) {
            return java.util.List.of(paymentAdministrationService.getForOrder(actor, orderId));
        }
        return paymentAdministrationService.list(actor, pageable);
    }

    @GetMapping("/{paymentId}")
    public PaymentResponse getPayment(
            @AuthenticationPrincipal AuthenticatedUser actor, @PathVariable UUID paymentId) {
        return paymentAdministrationService.get(actor, paymentId);
    }

    @PostMapping("/initiate")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'DISTRIBUTOR_ADMIN', 'DISTRIBUTOR_STAFF')")
    public ResponseEntity<PaymentInitiateResponse> initiatePayment(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody PaymentInitiateRequest request) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            request = new PaymentInitiateRequest(request.orderId(), request.phoneNumber(),
                    idempotencyKey, request.orderType(), request.provider());
        }
        return ResponseEntity.ok(paymentService.initiatePayment(actor, request));
    }

    /** Public by necessity; Daraja cannot present the platform JWT. */
    @PostMapping("/mpesa/callback")
    @SecurityRequirements
    public ResponseEntity<String> mpesaCallback(
            @RequestHeader(value = "X-Mpesa-Callback-Secret", required = false) String callbackSecret,
            @RequestBody MpesaCallbackRequest callbackRequest) {
        return ResponseEntity.ok(callbackHandling.handle(callbackSecret, callbackRequest));
    }

    @GetMapping("/{orderId}/status")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'DISTRIBUTOR_ADMIN', 'DISTRIBUTOR_STAFF')")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(paymentService.getPaymentStatus(actor, orderId));
    }
}
