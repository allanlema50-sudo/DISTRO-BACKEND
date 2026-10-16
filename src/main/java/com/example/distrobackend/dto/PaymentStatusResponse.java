package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.PaymentStatus;
import java.math.BigDecimal;
import java.util.UUID;

public record PaymentStatusResponse(
        UUID orderId,
        UUID paymentId,
        PaymentStatus status,
        BigDecimal amount,
        String mpesaReceiptNumber,
        String failureReason
) {}
