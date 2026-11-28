package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.PurchaseOrder;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"buyerOrganization", "supplierOrganization", "checkout", "destinationWarehouse"})
    Page<PurchaseOrder> findByBuyerOrganizationId(UUID organizationId, Pageable pageable);

    @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"buyerOrganization", "supplierOrganization", "checkout", "destinationWarehouse"})
    Page<PurchaseOrder> findBySupplierOrganizationId(UUID organizationId, Pageable pageable);

    @Query("select p from PurchaseOrder p "
            + "join fetch p.buyerOrganization buyer "
            + "join fetch p.supplierOrganization supplier "
            + "join fetch p.createdBy creator "
            + "left join fetch p.reviewedBy reviewer "
            + "left join fetch p.items i "
            + "left join fetch i.stockItem "
            + "left join fetch p.checkout checkout "
            + "where p.id = :id")
    Optional<PurchaseOrder> findByIdWithItems(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PurchaseOrder p join fetch p.buyerOrganization buyer "
            + "join fetch p.supplierOrganization supplier where p.id = :id")
    Optional<PurchaseOrder> findByIdForPayment(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PurchaseOrder p where p.id = :id")
    Optional<PurchaseOrder> findByIdForUpdate(@Param("id") UUID id);

    java.util.List<PurchaseOrder> findByCheckout_Id(UUID checkoutId);

    @Query("select distinct payment.id from CheckoutPayment payment "
            + "join payment.checkout checkout join PurchaseOrder po on po.checkout = checkout "
            + "where payment.status = com.example.distrobackend.Domain.enums.CheckoutPaymentStatus.SUCCESS "
            + "and po.status = com.example.distrobackend.Domain.enums.PurchaseOrderStatus.APPROVED "
            + "and (po.fulfilmentNextRecoveryAt is null or po.fulfilmentNextRecoveryAt <= :now) "
            + "and not exists (select shipment.id from PurchaseOrderShipment shipment "
            + "where shipment.purchaseOrder = po and shipment.status <> "
            + "com.example.distrobackend.Domain.enums.PurchaseOrderShipmentStatus.CANCELLED) "
            + "order by payment.id asc")
    Page<UUID> findCheckoutPaymentsNeedingFulfilmentRecovery(
            @Param("now") java.time.OffsetDateTime now, Pageable pageable);

    @Query(value = "select nextval('purchase_order_number_seq')", nativeQuery = true)
    Long getNextPurchaseOrderSequence();

}
