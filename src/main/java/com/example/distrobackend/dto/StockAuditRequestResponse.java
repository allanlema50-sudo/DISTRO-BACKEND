package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.StockAuditRequest;
import java.time.OffsetDateTime;
import java.util.UUID;

public record StockAuditRequestResponse(
        UUID id,
        UUID organizationId,
        String scope,
        String terminalId,
        String status,
        OffsetDateTime createdAt
) {
    public static StockAuditRequestResponse from(StockAuditRequest request) {
        return new StockAuditRequestResponse(request.getId(), request.getOrganization().getId(),
                request.getScope(), request.getTerminalId(), request.getStatus(), request.getCreatedAt());
    }
}
