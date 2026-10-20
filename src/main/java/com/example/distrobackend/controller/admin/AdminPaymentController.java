package com.example.distrobackend.controller.admin;

import com.example.distrobackend.dto.PaymentResponse;
import com.example.distrobackend.dto.PaymentStatusUpdateRequest;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.PaymentAdministrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/payments")
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentAdministrationService paymentAdministrationService;

    @GetMapping
    public Page<PaymentResponse> getAllPayments(
            @AuthenticationPrincipal AuthenticatedUser actor, Pageable pageable) {
        return paymentAdministrationService.list(actor, pageable);
    }

    @PatchMapping("/{paymentId}/status")
    public PaymentResponse updateStatus(@AuthenticationPrincipal AuthenticatedUser actor,
                                        @PathVariable UUID paymentId,
                                        @Valid @RequestBody PaymentStatusUpdateRequest request) {
        return paymentAdministrationService.updateStatus(actor, paymentId, request);
    }
}
