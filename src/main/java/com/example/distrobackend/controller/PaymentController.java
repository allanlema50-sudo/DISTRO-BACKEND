package com.example.distrobackend.controller;

import com.example.distrobackend.dto.PaymentInitiateRequest;
import com.example.distrobackend.dto.PaymentInitiateResponse;
import com.example.distrobackend.dto.PaymentStatusResponse;
import com.example.distrobackend.dto.mpesa.MpesaCallbackRequest;
import com.example.distrobackend.service.PaymentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/initiate")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'DISTRIBUTOR_ADMIN')")
    public ResponseEntity<PaymentInitiateResponse> initiatePayment(@Valid @RequestBody PaymentInitiateRequest request) {
        return ResponseEntity.ok(paymentService.initiatePayment(request));
    }

    @PostMapping("/mpesa/callback")
    // Daraja callbacks are sent from Safaricom's servers, which don't have your JWT tokens.
    // In production, you might secure this by checking IP whitelists or a custom auth header Daraja supports.
    // For now, we permit all (or you can exclude it in SecurityConfig).
    public ResponseEntity<String> mpesaCallback(@RequestBody MpesaCallbackRequest callbackRequest) {
        paymentService.processMpesaCallback(callbackRequest);
        // Safaricom expects a generic success response so they don't retry the webhook
        return ResponseEntity.ok("{\"ResultCode\":0, \"ResultDesc\":\"Success\"}");
    }

    @GetMapping("/{orderId}/status")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'DISTRIBUTOR_ADMIN')")
    public ResponseEntity<PaymentStatusResponse> getPaymentStatus(@PathVariable UUID orderId) {
        return ResponseEntity.ok(paymentService.getPaymentStatus(orderId));
    }
}
