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

    /**
     * Unpaid credit owed by one distributor to one manufacturer: each live credit
     * order's total less the money received against it. Calculated, never stored.
     */
    @Query(value = "select coalesce(sum(greatest(po.total_amount - coalesce(paid.amount_paid, 0), 0)), 0) "
            + "from purchase_orders po "
            + "left join (select purchase_order_id, sum(amount_paid) as amount_paid "
            + "           from purchase_order_settlements where status in ('PENDING', 'SETTLED') "
            + "           group by purchase_order_id) paid on paid.purchase_order_id = po.id "
            + "where po.supplier_organization_id = :manufacturerId "
            + "and po.buyer_organization_id = :distributorId "
            + "and po.payment_terms = 'CREDIT' and po.currency = :currency "
            + "and po.status in ('SUBMITTED', 'APPROVED', 'FULFILLED')", nativeQuery = true)
    java.math.BigDecimal outstandingCredit(@Param("manufacturerId") UUID manufacturerId,
                                           @Param("distributorId") UUID distributorId,
                                           @Param("currency") String currency);

    /** Number of credit orders for the pair that are unpaid and past their due date. */
    @Query(value = "select count(*) from purchase_orders po "
            + "left join (select purchase_order_id, sum(amount_paid) as amount_paid "
            + "           from purchase_order_settlements where status in ('PENDING', 'SETTLED') "
            + "           group by purchase_order_id) paid on paid.purchase_order_id = po.id "
            + "where po.supplier_organization_id = :manufacturerId "
            + "and po.buyer_organization_id = :distributorId "
            + "and po.payment_terms = 'CREDIT' and po.status in ('APPROVED', 'FULFILLED') "
            + "and po.due_at is not null and po.due_at < :now "
            + "and po.total_amount - coalesce(paid.amount_paid, 0) > 0", nativeQuery = true)
    long countOverdueCreditOrders(@Param("manufacturerId") UUID manufacturerId,
                                  @Param("distributorId") UUID distributorId,
                                  @Param("now") java.time.OffsetDateTime now);

    /** Credit orders past due that the nightly job has not yet flagged. */
    @Query(value = "select po.id from purchase_orders po "
            + "left join (select purchase_order_id, sum(amount_paid) as amount_paid "
            + "           from purchase_order_settlements where status in ('PENDING', 'SETTLED') "
            + "           group by purchase_order_id) paid on paid.purchase_order_id = po.id "
            + "where po.payment_terms = 'CREDIT' and po.status in ('APPROVED', 'FULFILLED') "
            + "and po.overdue_at is null and po.due_at is not null and po.due_at < :now "
            + "and po.total_amount - coalesce(paid.amount_paid, 0) > 0 "
            + "order by po.due_at asc limit :batchSize", nativeQuery = true)
    java.util.List<UUID> findCreditOrdersToFlagOverdue(@Param("now") java.time.OffsetDateTime now,
                                                       @Param("batchSize") int batchSize);

    @Query("select p from PurchaseOrder p join fetch p.buyerOrganization join fetch p.supplierOrganization "
            + "where p.supplierOrganization.id = :manufacturerId and p.buyerOrganization.id = :distributorId "
            + "and p.paymentTerms = com.example.distrobackend.Domain.enums.PaymentTerms.CREDIT "
            + "order by p.createdAt desc")
    java.util.List<PurchaseOrder> findCreditOrdersForPair(@Param("manufacturerId") UUID manufacturerId,
                                                          @Param("distributorId") UUID distributorId,
                                                          Pageable pageable);
}
