package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record CreditStatementResponse(
        DistributorCreditResponse credit,
        List<Line> orders
) {
    public record Line(
            UUID purchaseOrderId,
            String purchaseOrderNumber,
            PurchaseOrderStatus status,
            BigDecimal totalAmount,
            BigDecimal amountPaid,
            BigDecimal outstanding,
            OffsetDateTime createdAt,
            OffsetDateTime dueAt,
            boolean overdue,
            List<SettlementPaymentResponse> payments
    ) {}
}
