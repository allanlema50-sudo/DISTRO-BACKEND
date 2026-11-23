package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReviewPayoutBeneficiaryRequest(
        @NotNull Boolean approve,
        @Size(max = 500) String note
) {}
