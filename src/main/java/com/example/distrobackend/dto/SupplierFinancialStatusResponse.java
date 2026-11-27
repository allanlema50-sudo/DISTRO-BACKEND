package com.example.distrobackend.dto;
import java.math.BigDecimal;
import java.util.UUID;
public record SupplierFinancialStatusResponse(UUID purchaseOrderId, String checkoutReference, UUID allocationId,
        BigDecimal buyerPaidAmount, BigDecimal providerFee, BigDecimal commission, BigDecimal supplierAmount,
        String currency, String checkoutPaymentStatus, String paymentProvider, String providerMode,
        String settlementStatus, boolean demo, String label, String settlementNote) {}
