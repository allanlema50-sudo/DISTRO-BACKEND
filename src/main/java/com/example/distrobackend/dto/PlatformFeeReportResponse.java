package com.example.distrobackend.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record PlatformFeeReportResponse(
        LocalDate from,
        LocalDate to,
        String currency,
        List<Row> rows,
        BigDecimal totalFees
) {
    public record Row(UUID organizationId, String organizationName, String currency,
                      long feeCount, BigDecimal totalFees) {}
}
