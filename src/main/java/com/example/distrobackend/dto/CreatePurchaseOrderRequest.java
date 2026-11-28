package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.PaymentTerms;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreatePurchaseOrderRequest(
        @NotNull UUID supplierOrganizationId,
        @NotEmpty @Valid List<PurchaseOrderItemRequest> items,
        /** Only PAY_NOW is accepted. Omitted means PAY_NOW. */
        PaymentTerms paymentTerms
) {
    public CreatePurchaseOrderRequest(UUID supplierOrganizationId, List<PurchaseOrderItemRequest> items) {
        this(supplierOrganizationId, items, PaymentTerms.PAY_NOW);
    }

    public PaymentTerms effectivePaymentTerms() {
        if (paymentTerms == null || paymentTerms == PaymentTerms.PAY_NOW) {
            return PaymentTerms.PAY_NOW;
        }
        throw new com.example.distrobackend.Exception.ApiException(
                com.example.distrobackend.Exception.ErrorCode.BAD_REQUEST,
                "Purchase orders are pay-now only");
    }
}
