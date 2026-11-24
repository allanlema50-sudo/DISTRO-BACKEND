package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreatePaymentRefundRequest(
        @NotBlank @Size(max = 500) String reason
) {}
