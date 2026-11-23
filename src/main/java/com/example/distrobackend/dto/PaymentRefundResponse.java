package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.PaymentRefund;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record PaymentRefundResponse(
        UUID id, UUID checkoutId, UUID paymentId, String providerReference,
        String status, BigDecimal amount, String currency, String reason,
        String failureReason, OffsetDateTime processedAt, OffsetDateTime createdAt
) {
    public static PaymentRefundResponse from(PaymentRefund refund) {
        return new PaymentRefundResponse(refund.getId(), refund.getPayment().getCheckout().getId(),
                refund.getPayment().getId(), refund.getProviderReference(), refund.getStatus().name(),
                refund.getAmount(), refund.getCurrency(), refund.getReason(), refund.getFailureReason(),
                refund.getProcessedAt(), refund.getCreatedAt());
    }
}
