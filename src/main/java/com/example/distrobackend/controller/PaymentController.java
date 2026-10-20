package com.example.distrobackend.controller;

import com.example.distrobackend.dto.PaymentInitiateRequest;
import com.example.distrobackend.dto.PaymentInitiateResponse;
import com.example.distrobackend.dto.PaymentStatusResponse;
import com.example.distrobackend.dto.PaymentResponse;
import com.example.distrobackend.dto.mpesa.MpesaCallbackRequest;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.PaymentService;
import com.example.distrobackend.service.PaymentAdministrationService;
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
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentAdministrationService paymentAdministrationService;

    @GetMapping
    public Page<PaymentResponse> listPayments(
            @AuthenticationPrincipal AuthenticatedUser actor, Pageable pageable) {
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
            @Valid @RequestBody PaymentInitiateRequest request) {
        return ResponseEntity.ok(paymentService.initiatePayment(actor, request));
    }

    /** Public by necessity; Daraja cannot present the platform JWT. */
    @PostMapping("/mpesa/callback")
    @SecurityRequirements
    public ResponseEntity<String> mpesaCallback(
            @RequestHeader(value = "X-Mpesa-Callback-Secret", required = false) String callbackSecret,
            @RequestBody MpesaCallbackRequest callbackRequest) {
        paymentService.processMpesaCallback(callbackSecret, callbackRequest);
        return ResponseEntity.ok("{\"ResultCode\":0,\"ResultDesc\":\"Accepted\"}");
    }

    @GetMapping("/{orderId}/status")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'DISTRIBUTOR_ADMIN', 'DISTRIBUTOR_STAFF')")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @PathVariable UUID orderId) {
        return ResponseEntity.ok(paymentService.getPaymentStatus(actor, orderId));
    }
}
