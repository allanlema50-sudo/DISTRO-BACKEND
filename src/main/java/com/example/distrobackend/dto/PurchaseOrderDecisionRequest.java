package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.PurchaseOrderStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PurchaseOrderDecisionRequest(
        @NotNull PurchaseOrderStatus status,
        @Size(max = 1000) String note
) {}
