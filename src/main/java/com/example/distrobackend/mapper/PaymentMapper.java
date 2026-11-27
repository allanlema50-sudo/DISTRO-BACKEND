package com.example.distrobackend.mapper;

import com.example.distrobackend.Domain.entity.Payment;
import com.example.distrobackend.dto.PaymentResponse;

public class PaymentMapper {

    private PaymentMapper() { }

    public static PaymentResponse toResponse(Payment payment) {
        if (payment == null) return null;
        return new PaymentResponse(payment.getId(), payment.getOrder() == null ? null : payment.getOrder().getId(),
                payment.getAmount(), payment.getMethod(), payment.getStatus(), payment.getMpesaReceiptNumber(),
                payment.getInitiatedAt(), payment.getConfirmedAt(), "CUSTOMER_ORDER",
                payment.getProvider() == null ? "DARAJA" : payment.getProvider().name(), payment.getCurrency(),
                payment.getFailureReason(), payment.getProviderMode(), payment.getProviderReference(),
                payment.getProviderAuthorizationUrl());
    }
}
