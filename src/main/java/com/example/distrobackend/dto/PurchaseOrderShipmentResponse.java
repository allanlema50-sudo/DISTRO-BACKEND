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
        String buyerOrganizationName,
        String supplierOrganizationName,
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
        List<PurchaseOrderShipmentReceiptLineResponse> receiptLines,
        UUID originWarehouseId,
        String originWarehouseName,
        String originAddress,
        String originCity,
        Double originLatitude,
        Double originLongitude,
        String destinationAddress,
        String destinationCity,
        Double destinationLatitude,
        Double destinationLongitude,
        String routePlanningStatus
) {
    public static PurchaseOrderShipmentResponse from(PurchaseOrderShipment shipment) {
        return new PurchaseOrderShipmentResponse(
                shipment.getId(),
                shipment.getPurchaseOrder().getId(),
                shipment.getPurchaseOrder().getPurchaseOrderNumber(),
                shipment.getPurchaseOrder().getBuyerOrganization().getId(),
                shipment.getPurchaseOrder().getSupplierOrganization().getId(),
                displayName(shipment.getPurchaseOrder().getBuyerOrganization().getName()),
                displayName(shipment.getPurchaseOrder().getSupplierOrganization().getName()),
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
                        line.getPurchaseOrderItem().getProductNameSnapshot() == null ? line.getPurchaseOrderItem().getStockItem().getName() : line.getPurchaseOrderItem().getProductNameSnapshot(),
                        line.getExpectedQuantity(), line.getAcceptedQuantity(),
                        line.getDamagedQuantity(), line.getMissingQuantity())).toList(),
                shipment.getOriginWarehouse() == null ? null : shipment.getOriginWarehouse().getId(),
                shipment.getOriginWarehouse() == null ? null : shipment.getOriginWarehouse().getName(),
                shipment.getOriginWarehouse() == null ? null : shipment.getOriginWarehouse().getAddress(),
                shipment.getOriginWarehouse() == null ? null : shipment.getOriginWarehouse().getCity(),
                shipment.getOriginWarehouse() == null ? null : shipment.getOriginWarehouse().getLatitude(),
                shipment.getOriginWarehouse() == null ? null : shipment.getOriginWarehouse().getLongitude(),
                shipment.getDestinationWarehouse().getAddress(),
                shipment.getDestinationWarehouse().getCity(),
                shipment.getDestinationWarehouse().getLatitude(),
                shipment.getDestinationWarehouse().getLongitude(),
                hasRoutePins(shipment) ? "READY" : "LOCATION_DATA_REQUIRED");
    }

    private static boolean hasRoutePins(PurchaseOrderShipment shipment) {
        return shipment.getOriginWarehouse() != null
                && shipment.getOriginWarehouse().getLatitude() != null
                && shipment.getOriginWarehouse().getLongitude() != null
                && shipment.getDestinationWarehouse().getLatitude() != null
                && shipment.getDestinationWarehouse().getLongitude() != null;
    }

    private static String displayName(String name) {
        return name == null || name.isBlank() ? "Organization details unavailable" : name;
    }
}
