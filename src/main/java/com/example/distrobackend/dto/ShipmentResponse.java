package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.PurchaseOrderShipment;
import com.example.distrobackend.Domain.enums.ShipmentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ShipmentResponse(
        UUID id,
        UUID purchaseOrderId,
        String purchaseOrderNumber,
        UUID buyerOrganizationId,
        UUID supplierOrganizationId,
        UUID destinationWarehouseId,
        ShipmentStatus status,
        String trackingNumber,
        String carrier,
        String dispatchNote,
        String receiptNote,
        OffsetDateTime dispatchedAt,
        OffsetDateTime deliveredAt,
        OffsetDateTime receivedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static ShipmentResponse from(PurchaseOrderShipment shipment) {
        return new ShipmentResponse(
                shipment.getId(),
                shipment.getPurchaseOrder().getId(),
                shipment.getPurchaseOrder().getPurchaseOrderNumber(),
                shipment.getPurchaseOrder().getBuyerOrganization().getId(),
                shipment.getPurchaseOrder().getSupplierOrganization().getId(),
                shipment.getDestinationWarehouse().getId(),
                shipment.getStatus(),
                shipment.getTrackingNumber(),
                shipment.getCarrier(),
                shipment.getDispatchNote(),
                shipment.getReceiptNote(),
                shipment.getDispatchedAt(),
                shipment.getDeliveredAt(),
                shipment.getReceivedAt(),
                shipment.getCreatedAt(),
                shipment.getUpdatedAt());
    }
}
