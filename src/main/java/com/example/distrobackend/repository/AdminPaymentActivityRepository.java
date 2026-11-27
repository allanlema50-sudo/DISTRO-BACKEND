package com.example.distrobackend.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Read-only, cross-flow payment activity projection for platform administration. */
public interface AdminPaymentActivityRepository extends Repository<com.example.distrobackend.Domain.entity.Payment, UUID> {

    interface ActivityRow {
        UUID getTransactionId();
        String getTransactionType();
        String getProvider();
        String getProviderMode();
        String getStatus();
        String getMethod();
        BigDecimal getAmount();
        String getCurrency();
        String getProviderReference();
        OffsetDateTime getInitiatedAt();
        OffsetDateTime getConfirmedAt();
        UUID getOrganizationId();
        String getOrganizationName();
        String getInitiatorEmail();
        String getContextReference();
        boolean getDemo();
        BigDecimal getAllocatedGross();
        BigDecimal getProviderFees();
        BigDecimal getCommission();
        BigDecimal getSupplierPayable();
        long getSettlementCount();
        String getSettlementStatuses();
        long getRefundCount();
        BigDecimal getRefundedAmount();
        String getRefundStatuses();
        long getDisputeCount();
        long getOpenDisputeCount();
    }

    @Query(value = """
            SELECT p.id AS "transactionId", 'CUSTOMER_ORDER' AS "transactionType",
                   p.provider AS "provider", p.provider_mode AS "providerMode",
                   p.status AS "status", p.method AS "method", p.amount AS "amount",
                   p.currency AS "currency", p.provider_reference AS "providerReference",
                   p.initiated_at AS "initiatedAt", p.confirmed_at AS "confirmedAt",
                   o.organization_id AS "organizationId", org.name AS "organizationName",
                   customer.email AS "initiatorEmail",
                   o.order_number AS "contextReference", (p.provider = 'DEMO') AS "demo",
                   CAST(0 AS numeric) AS "allocatedGross", CAST(0 AS numeric) AS "providerFees",
                   CAST(0 AS numeric) AS "commission", CAST(0 AS numeric) AS "supplierPayable",
                   CAST(0 AS bigint) AS "settlementCount", CAST(NULL AS varchar) AS "settlementStatuses",
                   CAST(0 AS bigint) AS "refundCount", CAST(0 AS numeric) AS "refundedAmount",
                   CAST(NULL AS varchar) AS "refundStatuses", CAST(0 AS bigint) AS "disputeCount",
                   CAST(0 AS bigint) AS "openDisputeCount"
            FROM payments p
            JOIN orders o ON o.id = p.order_id
            JOIN organizations org ON org.id = o.organization_id
            JOIN users customer ON customer.id = o.customer_id
            WHERE (:fromInclusive IS NULL OR p.initiated_at >= :fromInclusive)
              AND (:toExclusive IS NULL OR p.initiated_at < :toExclusive)
              AND (:provider IS NULL OR p.provider = :provider)
              AND (:providerMode IS NULL OR p.provider_mode = :providerMode)
              AND (:status IS NULL OR p.status = :status)
              AND (:organizationId IS NULL OR o.organization_id = :organizationId)
              AND (:search IS NULL OR p.provider_reference ILIKE CONCAT('%', :search, '%')
                   OR o.order_number ILIKE CONCAT('%', :search, '%')
                   OR org.name ILIKE CONCAT('%', :search, '%'))
            UNION ALL
            SELECT cp.id AS "transactionId", 'MARKETPLACE_CHECKOUT' AS "transactionType",
                   cp.provider AS "provider", cp.provider_mode AS "providerMode",
                   cp.status AS "status", cp.method AS "method", cp.amount AS "amount",
                   cp.currency AS "currency", cp.provider_reference AS "providerReference",
                   cp.initiated_at AS "initiatedAt", cp.confirmed_at AS "confirmedAt",
                   c.buyer_organization_id AS "organizationId", org.name AS "organizationName",
                   initiator.email AS "initiatorEmail",
                   c.checkout_reference AS "contextReference", cp.demo AS "demo",
                   COALESCE((SELECT SUM(a.gross_amount) FROM payment_allocations a WHERE a.payment_id = cp.id), 0) AS "allocatedGross",
                   COALESCE((SELECT SUM(a.provider_fee) FROM payment_allocations a WHERE a.payment_id = cp.id), 0) AS "providerFees",
                   COALESCE((SELECT SUM(a.commission) FROM payment_allocations a WHERE a.payment_id = cp.id), 0) AS "commission",
                   COALESCE((SELECT SUM(a.supplier_amount) FROM payment_allocations a WHERE a.payment_id = cp.id), 0) AS "supplierPayable",
                   (SELECT COUNT(*) FROM supplier_settlements ss JOIN payment_allocations a ON a.id = ss.payment_allocation_id WHERE a.payment_id = cp.id) AS "settlementCount",
                   (SELECT string_agg(DISTINCT ss.status, ',' ORDER BY ss.status) FROM supplier_settlements ss JOIN payment_allocations a ON a.id = ss.payment_allocation_id WHERE a.payment_id = cp.id) AS "settlementStatuses",
                   (SELECT COUNT(*) FROM payment_refunds r WHERE r.checkout_payment_id = cp.id) AS "refundCount",
                   COALESCE((SELECT SUM(r.amount) FROM payment_refunds r WHERE r.checkout_payment_id = cp.id AND r.status = 'PROCESSED'), 0) AS "refundedAmount",
                   (SELECT string_agg(DISTINCT r.status, ',' ORDER BY r.status) FROM payment_refunds r WHERE r.checkout_payment_id = cp.id) AS "refundStatuses",
                   (SELECT COUNT(*) FROM payment_disputes d WHERE d.checkout_payment_id = cp.id) AS "disputeCount",
                   (SELECT COUNT(*) FROM payment_disputes d WHERE d.checkout_payment_id = cp.id AND d.cleared_at IS NULL) AS "openDisputeCount"
            FROM checkout_payments cp
            JOIN checkouts c ON c.id = cp.checkout_id
            LEFT JOIN organizations org ON org.id = c.buyer_organization_id
            JOIN users initiator ON initiator.id = c.created_by
            WHERE (:fromInclusive IS NULL OR cp.initiated_at >= :fromInclusive)
              AND (:toExclusive IS NULL OR cp.initiated_at < :toExclusive)
              AND (:provider IS NULL OR cp.provider = :provider)
              AND (:providerMode IS NULL OR cp.provider_mode = :providerMode)
              AND (:status IS NULL OR cp.status = :status)
              AND (:organizationId IS NULL OR c.buyer_organization_id = :organizationId
                   OR EXISTS (SELECT 1 FROM payment_allocations oa
                              WHERE oa.payment_id = cp.id AND oa.supplier_organization_id = :organizationId))
              AND (:search IS NULL OR cp.provider_reference ILIKE CONCAT('%', :search, '%')
                   OR c.checkout_reference ILIKE CONCAT('%', :search, '%')
                   OR COALESCE(org.name, '') ILIKE CONCAT('%', :search, '%')
                   OR EXISTS (SELECT 1 FROM payment_allocations sa
                              JOIN organizations so ON so.id = sa.supplier_organization_id
                              WHERE sa.payment_id = cp.id AND so.name ILIKE CONCAT('%', :search, '%')))
            ORDER BY "initiatedAt" DESC, "transactionId" DESC
            """, countQuery = """
            SELECT COUNT(*) FROM (
                SELECT p.id
                FROM payments p JOIN orders o ON o.id = p.order_id
                JOIN organizations org ON org.id = o.organization_id
                WHERE (:fromInclusive IS NULL OR p.initiated_at >= :fromInclusive)
                  AND (:toExclusive IS NULL OR p.initiated_at < :toExclusive)
                  AND (:provider IS NULL OR p.provider = :provider)
                  AND (:providerMode IS NULL OR p.provider_mode = :providerMode)
                  AND (:status IS NULL OR p.status = :status)
                  AND (:organizationId IS NULL OR o.organization_id = :organizationId)
                  AND (:search IS NULL OR p.provider_reference ILIKE CONCAT('%', :search, '%')
                       OR o.order_number ILIKE CONCAT('%', :search, '%') OR org.name ILIKE CONCAT('%', :search, '%'))
                UNION ALL
                SELECT cp.id
                FROM checkout_payments cp JOIN checkouts c ON c.id = cp.checkout_id
                LEFT JOIN organizations org ON org.id = c.buyer_organization_id
                WHERE (:fromInclusive IS NULL OR cp.initiated_at >= :fromInclusive)
                  AND (:toExclusive IS NULL OR cp.initiated_at < :toExclusive)
                  AND (:provider IS NULL OR cp.provider = :provider)
                  AND (:providerMode IS NULL OR cp.provider_mode = :providerMode)
                  AND (:status IS NULL OR cp.status = :status)
                  AND (:organizationId IS NULL OR c.buyer_organization_id = :organizationId
                       OR EXISTS (SELECT 1 FROM payment_allocations oa
                                  WHERE oa.payment_id = cp.id AND oa.supplier_organization_id = :organizationId))
                  AND (:search IS NULL OR cp.provider_reference ILIKE CONCAT('%', :search, '%')
                       OR c.checkout_reference ILIKE CONCAT('%', :search, '%')
                       OR COALESCE(org.name, '') ILIKE CONCAT('%', :search, '%')
                       OR EXISTS (SELECT 1 FROM payment_allocations sa
                                  JOIN organizations so ON so.id = sa.supplier_organization_id
                                  WHERE sa.payment_id = cp.id AND so.name ILIKE CONCAT('%', :search, '%')))
            ) activity
            """, nativeQuery = true)
    Page<ActivityRow> search(
            @Param("fromInclusive") OffsetDateTime fromInclusive,
            @Param("toExclusive") OffsetDateTime toExclusive,
            @Param("provider") String provider,
            @Param("providerMode") String providerMode,
            @Param("status") String status,
            @Param("organizationId") UUID organizationId,
            @Param("search") String search,
            Pageable pageable);
}
