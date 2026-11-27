package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.PurchaseOrderStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record PurchaseOrderResponse(
        UUID id,
        String purchaseOrderNumber,
        UUID buyerOrganizationId,
        UUID supplierOrganizationId,
        String buyerOrganizationName,
        String supplierOrganizationName,
        PurchaseOrderStatus status,
        BigDecimal totalAmount,
        String currency,
        String rejectionReason,
        OffsetDateTime submittedAt,
        OffsetDateTime reviewedAt,
        OffsetDateTime fulfilledAt,
        List<PurchaseOrderItemResponse> items,
        List<PurchaseOrderSettlementResponse> settlements,
        UUID checkoutId,
        String checkoutReference,
        UUID destinationWarehouseId,
        String checkoutPaymentStatus,
        boolean checkoutPaymentDemo,
        String checkoutPaymentReference,
        String paymentStatus,
        String paymentProvider,
        BigDecimal paidAmount,
        OffsetDateTime paymentConfirmedAt,
        String fulfilmentStatus,
        boolean dispatchEligible,
        int packageCount,
        com.example.distrobackend.Domain.enums.PaymentTerms paymentTerms,
        OffsetDateTime dueAt,
        boolean overdue,
        BigDecimal amountPaid,
        BigDecimal amountOutstanding
) {}

