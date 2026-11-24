package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateSettlementBatchRequest(
        @NotEmpty @Size(max = 100) List<@NotNull UUID> settlementIds,
        @Size(max = 500) String note
) {}
