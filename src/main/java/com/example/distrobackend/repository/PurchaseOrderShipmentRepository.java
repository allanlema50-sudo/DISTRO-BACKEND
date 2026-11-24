package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.PurchaseOrderShipment;
import com.example.distrobackend.Domain.enums.PurchaseOrderShipmentStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderShipmentRepository extends JpaRepository<PurchaseOrderShipment, UUID> {

    @Query("select coalesce(sum(r.acceptedQuantity), 0) from PurchaseOrderShipmentReceiptLine r "
            + "where r.purchaseOrderItem.stockItem.id = :productId "
            + "and r.purchaseOrderItem.purchaseOrder.buyerOrganization.id = :distributorId "
            + "and r.shipment.status = com.example.distrobackend.Domain.enums.PurchaseOrderShipmentStatus.RECEIVED")
    Long sumAcceptedQuantityForDistributorProduct(@Param("distributorId") UUID distributorId,
                                                   @Param("productId") UUID productId);

    @Query("select s from PurchaseOrderShipment s "
            + "join fetch s.purchaseOrder p "
            + "join fetch p.buyerOrganization buyer "
            + "join fetch p.supplierOrganization supplier "
            + "join fetch s.destinationWarehouse warehouse "
            + "where s.id = :id")
    Optional<PurchaseOrderShipment> findByIdWithDetails(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PurchaseOrderShipment s "
            + "join fetch s.purchaseOrder p "
            + "join fetch p.buyerOrganization buyer "
            + "join fetch p.supplierOrganization supplier "
            + "join fetch s.destinationWarehouse warehouse "
            + "where s.id = :id")
    Optional<PurchaseOrderShipment> findByIdWithDetailsForUpdate(@Param("id") UUID id);

    @Query("select s from PurchaseOrderShipment s "
            + "join fetch s.purchaseOrder p "
            + "join fetch p.buyerOrganization buyer "
            + "join fetch p.supplierOrganization supplier "
            + "join fetch s.destinationWarehouse warehouse "
            + "where p.id = :purchaseOrderId")
    Optional<PurchaseOrderShipment> findByPurchaseOrderId(@Param("purchaseOrderId") UUID purchaseOrderId);

    @EntityGraph(attributePaths = {"purchaseOrder", "purchaseOrder.buyerOrganization",
            "purchaseOrder.supplierOrganization", "destinationWarehouse"})
    Page<PurchaseOrderShipment> findByPurchaseOrder_BuyerOrganization_IdOrderByCreatedAtDesc(
            UUID organizationId, Pageable pageable);

    @EntityGraph(attributePaths = {"purchaseOrder", "purchaseOrder.buyerOrganization",
            "purchaseOrder.supplierOrganization", "destinationWarehouse"})
    Page<PurchaseOrderShipment> findByPurchaseOrder_SupplierOrganization_IdOrderByCreatedAtDesc(
            UUID organizationId, Pageable pageable);

    @EntityGraph(attributePaths = {"purchaseOrder", "purchaseOrder.buyerOrganization",
            "purchaseOrder.supplierOrganization", "destinationWarehouse", "originWarehouse"})
    Page<PurchaseOrderShipment> findByPurchaseOrder_SupplierOrganization_IdAndStatusOrderByCreatedAtDesc(
            UUID organizationId, PurchaseOrderShipmentStatus status, Pageable pageable);

    boolean existsByPurchaseOrder_Id(UUID purchaseOrderId);

    long countByStatus(PurchaseOrderShipmentStatus status);
}
