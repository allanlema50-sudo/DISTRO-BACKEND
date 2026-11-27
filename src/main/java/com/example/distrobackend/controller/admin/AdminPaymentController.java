package com.example.distrobackend.controller.admin;

import com.example.distrobackend.dto.PaymentResponse;
import com.example.distrobackend.dto.PaymentStatusUpdateRequest;
import com.example.distrobackend.dto.ReversePaymentRequest;
import com.example.distrobackend.dto.AdminPaymentActivityItem;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.AdminPaymentActivityService;
import com.example.distrobackend.service.PaymentAdministrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import java.time.OffsetDateTime;

import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/admin/payments", "/api/admin/payments"})
@PreAuthorize("hasRole('SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminPaymentController {

    private final PaymentAdministrationService paymentAdministrationService;
    private final AdminPaymentActivityService adminPaymentActivityService;

    @GetMapping("/activity")
    public Page<AdminPaymentActivityItem> getActivity(
            @AuthenticationPrincipal AuthenticatedUser actor,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String providerMode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID organizationId,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return adminPaymentActivityService.search(actor, from, to, provider, providerMode,
                status, organizationId, search, page, size);
    }

    @GetMapping
    public Page<PaymentResponse> getAllPayments(
            @AuthenticationPrincipal AuthenticatedUser actor, Pageable pageable) {
        return paymentAdministrationService.list(actor, pageable);
    }

    @GetMapping("/{paymentId}")
    public PaymentResponse getPayment(@AuthenticationPrincipal AuthenticatedUser actor,
                                      @PathVariable UUID paymentId) {
        return paymentAdministrationService.get(actor, paymentId);
    }

    @PostMapping("/{paymentId}/reverse")
    public PaymentResponse reverse(@AuthenticationPrincipal AuthenticatedUser actor,
                                   @PathVariable UUID paymentId,
                                   @Valid @RequestBody(required = false) ReversePaymentRequest request) {
        String note = request == null ? null : request.note();
        return paymentAdministrationService.reverse(actor, paymentId, note);
    }

    @PatchMapping("/{paymentId}/status")
    public PaymentResponse updateStatus(@AuthenticationPrincipal AuthenticatedUser actor,
                                        @PathVariable UUID paymentId,
                                        @Valid @RequestBody PaymentStatusUpdateRequest request) {
        return paymentAdministrationService.updateStatus(actor, paymentId, request);
    }
}
