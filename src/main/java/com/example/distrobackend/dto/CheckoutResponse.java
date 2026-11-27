package com.example.distrobackend.dto;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
public record CheckoutResponse(UUID id, String checkoutReference, String status, BigDecimal totalAmount, String currency, UUID buyerOrganizationId, String buyerOrganizationName, boolean demo, String demoLabel, Payment payment, List<SupplierOrder> supplierOrders, List<Allocation> allocations) {
 public record Payment(UUID id, String checkoutReference, String method, String provider, String status, String providerReference,
                       String providerMode,
                       String authorizationUrl, String accessCode, boolean demo, String demoLabel,
                       java.time.OffsetDateTime initiatedAt, java.time.OffsetDateTime confirmedAt,
                       java.time.OffsetDateTime updatedAt, boolean terminal, boolean retryAllowed, String userMessage) {}
 public record SupplierOrder(UUID purchaseOrderId, String purchaseOrderNumber, UUID supplierOrganizationId, String supplierOrganizationName, UUID destinationWarehouseId, String status, BigDecimal totalAmount, String currency, List<Item> items) {}
 public record Item(UUID productId, String productName, String sku, String imageUrl, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}
 public record Allocation(UUID id, UUID purchaseOrderId, UUID supplierOrganizationId, String supplierOrganizationName, BigDecimal grossAmount, BigDecimal providerFee, BigDecimal commission, BigDecimal supplierAmount, String currency, boolean demo, String settlementStatus, String settlementNote) {}
}
