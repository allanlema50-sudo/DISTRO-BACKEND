package com.example.distrobackend.service;

import java.util.UUID;

public record PurchaseOrderPaymentVerifiedEvent(UUID checkoutPaymentId, UUID purchaseOrderId) {
    public static PurchaseOrderPaymentVerifiedEvent checkout(UUID paymentId) {
        return new PurchaseOrderPaymentVerifiedEvent(paymentId, null);
    }
    public static PurchaseOrderPaymentVerifiedEvent purchaseOrder(UUID purchaseOrderId) {
        return new PurchaseOrderPaymentVerifiedEvent(null, purchaseOrderId);
    }
}
