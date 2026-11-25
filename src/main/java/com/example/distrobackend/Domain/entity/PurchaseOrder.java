package com.example.distrobackend.Domain.entity;

import com.example.distrobackend.Domain.enums.PaymentTerms;
import com.example.distrobackend.Domain.enums.PurchaseOrderStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_orders", indexes = {
        @Index(name = "idx_purchase_orders_buyer_created", columnList = "buyer_organization_id,created_at"),
        @Index(name = "idx_purchase_orders_supplier_status", columnList = "supplier_organization_id,status")
})
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrder {

    @Id
    @GeneratedValue
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID id;

    @Column(name = "purchase_order_number", nullable = false, unique = true, length = 30)
    private String purchaseOrderNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "buyer_organization_id", nullable = false)
    private Organization buyerOrganization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "supplier_organization_id", nullable = false)
    private Organization supplierOrganization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkout_id")
    private Checkout checkout;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_warehouse_id")
    private Warehouse destinationWarehouse;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PurchaseOrderStatus status = PurchaseOrderStatus.SUBMITTED;

    @Column(name = "stock_reserved", nullable = false)
    private boolean stockReserved = false;

    @Column(name = "package_count", nullable = false)
    private int packageCount;

    @Column(name = "fulfilment_status", nullable = false, length = 40)
    private String fulfilmentStatus = "PAYMENT_PENDING";

    @Column(name = "fulfilment_recovery_attempts", nullable = false)
    private int fulfilmentRecoveryAttempts;

    @Column(name = "fulfilment_next_recovery_at")
    private OffsetDateTime fulfilmentNextRecoveryAt;

    @Column(name = "fulfilment_last_recovery_error", columnDefinition = "TEXT")
    private String fulfilmentLastRecoveryError;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "KES";

    @Column(name = "rejection_reason", columnDefinition = "TEXT")
    private String rejectionReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_terms", nullable = false, length = 20)
    private PaymentTerms paymentTerms = PaymentTerms.PAY_NOW;

    /** Credit purchase orders only: approval time plus the agreed term days. */
    @Column(name = "due_at")
    private OffsetDateTime dueAt;

    /** First time the nightly job saw this credit order unpaid past due_at. */
    @Column(name = "overdue_at")
    private OffsetDateTime overdueAt;

    /** Version of the relationship terms applied; null when a platform default applied. */
    @Column(name = "credit_terms_version")
    private Long creditTermsVersion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "submitted_at", nullable = false)
    private OffsetDateTime submittedAt;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "fulfilled_at")
    private OffsetDateTime fulfilledAt;

    @OneToMany(mappedBy = "purchaseOrder", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PurchaseOrderItem> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public void addItem(PurchaseOrderItem item) {
        items.add(item);
        item.setPurchaseOrder(this);
    }
}
