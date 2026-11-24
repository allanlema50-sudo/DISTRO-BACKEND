package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePaymentDisputeReviewRequest(
        @NotBlank @Size(max = 2000) String reviewNote,
        boolean clearForSettlement
) {}
