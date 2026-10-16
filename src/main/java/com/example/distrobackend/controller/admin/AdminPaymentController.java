
package com.example.distrobackend.controller.admin;

import com.example.distrobackend.Domain.enums.PaymentMethod;
import com.example.distrobackend.Domain.enums.PaymentStatus;
import com.example.distrobackend.Domain.entity.Payment;
import com.example.distrobackend.dto.AdminPaymentResponse;
import com.example.distrobackend.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('PLATFORM_ADMIN', 'SUPER_ADMIN')")
public class AdminPaymentController {

    private final PaymentRepository paymentRepository;

    @GetMapping
    @Transactional(readOnly = true)
    public Page<AdminPaymentResponse> getPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String method
    ) {
        PaymentStatus paymentStatus = parseStatus(status);
        PaymentMethod paymentMethod = parseMethod(method);

           Pageable pageable = PageRequest.of(
                Math.max(page, 0),
                Math.min(Math.max(size, 1), 100),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
String normalizedSearch = search == null ? "" : search.trim();
        return paymentRepository.findAdminPayments(
                        normalizedSearch,
                        paymentStatus,
                        paymentMethod,
                        pageable
                )
                .map(AdminPaymentResponse::from);
    }

    @GetMapping("/{paymentId}")
    @Transactional(readOnly = true)
    public AdminPaymentResponse getPayment(
            @PathVariable UUID paymentId
    ) {
        Payment payment = paymentRepository
                .findDetailedById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found"
                ));

        return AdminPaymentResponse.from(payment);
    }

    private PaymentStatus parseStatus(String status) {
        if (status == null || status.isBlank()
                || status.equalsIgnoreCase("ALL")) {
            return null;
        }

        String normalized = status.trim().toUpperCase();

        if (normalized.equals("PAID")) {
            normalized = "CONFIRMED";
        } else if (normalized.equals("REFUNDED")) {
            normalized = "REVERSED";
        }

        try {
            return PaymentStatus.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported payment status: " + status
            );
        }
    }

    private PaymentMethod parseMethod(String method) {
        if (method == null || method.isBlank()
                || method.equalsIgnoreCase("ALL")) {
            return null;
        }

        try {
            return PaymentMethod.valueOf(
                    method.trim().toUpperCase()
            );
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Unsupported payment method: " + method
            );
        }
    }
}
