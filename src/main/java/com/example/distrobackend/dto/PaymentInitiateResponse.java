package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.PaymentStatus;
import java.util.UUID;

public record PaymentInitiateResponse(
        UUID paymentId,
        String mpesaCheckoutRequestId, // Store this to track the push status
        PaymentStatus status,
        String customerMessage // e.g., "Success. Request accepted for processing"
) {}
