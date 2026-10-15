package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.Payment;
import com.example.distrobackend.Domain.enums.PaymentMethod;
import com.example.distrobackend.Domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        BigDecimal amount,
        PaymentMethod method,
        PaymentStatus status,
        String mpesaReceiptNumber,
        OffsetDateTime initiatedAt,
        OffsetDateTime confirmedAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder() != null ? payment.getOrder().getId() : null,
                payment.getAmount(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getMpesaReceiptNumber(),
                payment.getInitiatedAt(),
                payment.getConfirmedAt()
        );
    }
}
