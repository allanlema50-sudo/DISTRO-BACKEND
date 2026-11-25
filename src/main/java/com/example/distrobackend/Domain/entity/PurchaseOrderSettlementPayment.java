package com.example.distrobackend.Domain.entity;

import com.example.distrobackend.Domain.enums.SettlementMethod;
import com.example.distrobackend.Domain.enums.SettlementPaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One payment toward a purchase-order settlement. A bank slip is SUBMITTED by
 * the distributor with no amount; the supplier's administrator records the
 * amount received when confirming it. The unique (supplier, payer reference)
 * index means one slip or receipt can never settle two orders.
 */
@Entity
@Table(name = "purchase_order_settlement_payments")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrderSettlementPayment {

    @Id
    @GeneratedValue
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "settlement_id", nullable = false)
    private PurchaseOrderSettlement settlement;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_organization_id", nullable = false)
    private Organization supplierOrganization;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 20)
    private SettlementMethod method;

    @Column(name = "payer_reference", nullable = false, length = 100)
    private String payerReference;

    /** Null until the supplier confirms the amount received. */
    @Column(name = "amount", precision = 12, scale = 2)
    private BigDecimal amount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proof_document_id")
    private SettlementProofDocument proofDocument;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private SettlementPaymentStatus status = SettlementPaymentStatus.SUBMITTED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by")
    private User submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt = OffsetDateTime.now();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_by")
    private User confirmedBy;

    @Column(name = "confirmed_at")
    private OffsetDateTime confirmedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
