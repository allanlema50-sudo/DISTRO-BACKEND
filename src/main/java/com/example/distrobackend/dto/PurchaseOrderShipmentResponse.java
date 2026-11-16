package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.PurchaseOrderShipment;
import com.example.distrobackend.Domain.enums.PurchaseOrderShipmentStatus;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.List;

public record PurchaseOrderShipmentResponse(
        UUID id,
        UUID purchaseOrderId,
        String purchaseOrderNumber,
        UUID buyerOrganizationId,
        UUID supplierOrganizationId,
        UUID destinationWarehouseId,
        PurchaseOrderShipmentStatus status,
        String trackingNumber,
        String carrier,
        String dispatchNote,
        String manufacturerNote,
        String receiptNote,
        OffsetDateTime dispatchedAt,
        OffsetDateTime deliveredAt,
        OffsetDateTime receivedAt,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        List<PurchaseOrderShipmentReceiptLineResponse> receiptLines
) {
    public static PurchaseOrderShipmentResponse from(PurchaseOrderShipment shipment) {
        return new PurchaseOrderShipmentResponse(
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
                shipment.getManufacturerNote(),
                shipment.getReceiptNote(),
                shipment.getDispatchedAt(),
                shipment.getDeliveredAt(),
                shipment.getReceivedAt(),
                shipment.getCreatedAt(),
                shipment.getUpdatedAt(),
                shipment.getReceiptLines().stream().map(line -> new PurchaseOrderShipmentReceiptLineResponse(
                        line.getPurchaseOrderItem().getId(),
                        line.getPurchaseOrderItem().getStockItem().getId(),
                        line.getPurchaseOrderItem().getStockItem().getName(),
                        line.getExpectedQuantity(), line.getAcceptedQuantity(),
                        line.getDamagedQuantity(), line.getMissingQuantity())).toList());
    }
}
