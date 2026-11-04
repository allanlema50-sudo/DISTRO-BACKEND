package com.example.distrobackend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StockAuditRequestCreate(
        @NotBlank @Size(max = 30) String scope,
        @NotBlank @Size(max = 100) String terminalId
) {}
