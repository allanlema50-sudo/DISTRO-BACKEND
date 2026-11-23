package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.PurchaseOrderShipmentGroup;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderShipmentGroupRepository extends JpaRepository<PurchaseOrderShipmentGroup, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select shipmentGroup from PurchaseOrderShipmentGroup shipmentGroup where shipmentGroup.trip.id = :tripId")
    java.util.List<PurchaseOrderShipmentGroup> findByTrip_IdForUpdate(@Param("tripId") UUID tripId);

    @Query("select groupEntity.id from PurchaseOrderShipmentGroup groupEntity "
            + "join groupEntity.shipments shipment where shipment.id = :shipmentId")
    Optional<UUID> findIdByShipmentId(@Param("shipmentId") UUID shipmentId);
    Page<PurchaseOrderShipmentGroup> findByManufacturerOrganization_IdOrderByCreatedAtDesc(
            UUID manufacturerId, Pageable pageable);

    boolean existsByShipments_Id(UUID purchaseOrderShipmentId);

    @Query("select distinct shipmentGroup from PurchaseOrderShipmentGroup shipmentGroup "
            + "join fetch shipmentGroup.manufacturerOrganization "
            + "join fetch shipmentGroup.originWarehouse "
            + "join fetch shipmentGroup.destinationWarehouse "
            + "left join fetch shipmentGroup.trip "
            + "left join fetch shipmentGroup.shipments shipment "
            + "left join fetch shipment.purchaseOrder purchaseOrder "
            + "left join fetch purchaseOrder.items item "
            + "left join fetch item.stockItem "
            + "where shipmentGroup.id = :id")
    Optional<PurchaseOrderShipmentGroup> findWithDetails(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select shipmentGroup from PurchaseOrderShipmentGroup shipmentGroup where shipmentGroup.id = :id")
    Optional<PurchaseOrderShipmentGroup> findByIdForUpdate(@Param("id") UUID id);
}
