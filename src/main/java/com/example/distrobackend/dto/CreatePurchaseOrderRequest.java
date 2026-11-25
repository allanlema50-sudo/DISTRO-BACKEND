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
        /** PAY_NOW when omitted. CREDIT needs credit terms from the supplier. */
        PaymentTerms paymentTerms
) {
    public CreatePurchaseOrderRequest(UUID supplierOrganizationId, List<PurchaseOrderItemRequest> items) {
        this(supplierOrganizationId, items, PaymentTerms.PAY_NOW);
    }

    public PaymentTerms effectivePaymentTerms() {
        return paymentTerms == null ? PaymentTerms.PAY_NOW : paymentTerms;
    }
}
