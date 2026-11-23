package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.entity.PurchaseOrderShipmentGroup;
import com.example.distrobackend.Domain.enums.ManufacturerShipmentStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ShipmentConsolidationResponse(
        UUID id,
        String shipmentReference,
        UUID manufacturerOrganizationId,
        UUID originWarehouseId,
        String originWarehouseName,
        String originAddress,
        String originCity,
        Double originLatitude,
        Double originLongitude,
        UUID destinationWarehouseId,
        String destinationWarehouseName,
        String destinationAddress,
        String destinationCity,
        Double destinationLatitude,
        Double destinationLongitude,
        String routePlanningStatus,
        ManufacturerShipmentStatus status,
        UUID tripId,
        List<PurchaseOrderShipmentResponse> consignments,
        OffsetDateTime createdAt,
        OffsetDateTime dispatchedAt) {

    public static ShipmentConsolidationResponse from(PurchaseOrderShipmentGroup group) {
        var origin = group.getOriginWarehouse();
        var destination = group.getDestinationWarehouse();
        return new ShipmentConsolidationResponse(group.getId(), group.getShipmentReference(),
                group.getManufacturerOrganization().getId(), origin.getId(), origin.getName(),
                origin.getAddress(), origin.getCity(), origin.getLatitude(), origin.getLongitude(),
                destination.getId(), destination.getName(), destination.getAddress(), destination.getCity(),
                destination.getLatitude(), destination.getLongitude(),
                origin.getLatitude() != null && origin.getLongitude() != null
                        && destination.getLatitude() != null && destination.getLongitude() != null
                        ? "READY" : "LOCATION_DATA_REQUIRED",
                group.getStatus(), group.getTrip() == null ? null : group.getTrip().getId(),
                group.getShipments().stream().map(PurchaseOrderShipmentResponse::from).toList(),
                group.getCreatedAt(), group.getDispatchedAt());
    }
}
