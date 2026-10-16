
package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.Payment;
import com.example.distrobackend.Domain.enums.PaymentMethod;
import com.example.distrobackend.Domain.enums.PaymentStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AdminPaymentResponse(
        UUID id,
        UUID orderId,
        String orderNumber,
        String customerName,
        String organizationName,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        PaymentStatus status,
        String transactionReference,
        String phoneNumber,
        OffsetDateTime paymentDate,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String failureReason
) {
    public static AdminPaymentResponse from(Payment payment) {
        var order = payment.getOrder();

        String customerName = order.getCustomer() != null
                ? order.getCustomer().getFullName()
                : null;

        String organizationName = order.getOrganization() != null
                ? order.getOrganization().getName()
                : null;

        OffsetDateTime paymentDate = payment.getConfirmedAt() != null
                ? payment.getConfirmedAt()
                : payment.getInitiatedAt();

        return new AdminPaymentResponse(
                payment.getId(),
                order.getId(),
                order.getOrderNumber(),
                customerName,
                organizationName,
                payment.getAmount(),
                payment.getCurrency(),
                payment.getMethod(),
                payment.getStatus(),
                payment.getMpesaReceiptNumber(),
                payment.getPhoneNumber(),
                paymentDate,
                payment.getCreatedAt(),
                payment.getUpdatedAt(),
                payment.getFailureReason()
        );
    }
}
