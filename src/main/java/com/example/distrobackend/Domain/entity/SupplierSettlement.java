package com.example.distrobackend.Domain.entity;
import com.example.distrobackend.Domain.enums.SupplierSettlementStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
import com.example.distrobackend.Domain.enums.PaymentProvider;
@Entity
@Table(name="supplier_settlements", indexes=@Index(name="idx_supplier_settlement_supplier_status", columnList="supplier_organization_id,status"))
@Getter @Setter @NoArgsConstructor
public class SupplierSettlement {
 @Id @GeneratedValue @JdbcTypeCode(SqlTypes.UUID) private UUID id;
 @OneToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="payment_allocation_id", nullable=false, unique=true) private PaymentAllocation allocation;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="supplier_organization_id", nullable=false) private Organization supplierOrganization;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="beneficiary_id") private PayoutBeneficiary beneficiary;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="settlement_batch_id") private SettlementBatch settlementBatch;
 @Enumerated(EnumType.STRING) @Column(name="provider", length=20) private PaymentProvider provider;
 @Enumerated(EnumType.STRING) @Column(name="status", nullable=false, length=20) private SupplierSettlementStatus status=SupplierSettlementStatus.PENDING;
 @Column(name="amount", nullable=false, precision=14, scale=2) private BigDecimal amount;
 @Column(name="currency", nullable=false, length=3) private String currency="KES";
 @Column(name="payout_reference", unique=true, length=120) private String payoutReference;
 @Column(name="provider_transfer_code", length=120) private String providerTransferCode;
 @Column(name="payout_attempts", nullable=false) private int payoutAttempts;
 @Column(name="last_attempt_at") private OffsetDateTime lastAttemptAt;
 @Column(name="failure_reason", columnDefinition="TEXT") private String failureReason;
 @Column(name="demo", nullable=false) private boolean demo;
 @Column(name="note", columnDefinition="TEXT") private String note;

 @Column(name="settled_at") private OffsetDateTime settledAt;
 @CreationTimestamp @Column(name="created_at", nullable=false, updatable=false) private OffsetDateTime createdAt;
 @UpdateTimestamp @Column(name="updated_at", nullable=false) private OffsetDateTime updatedAt;
 @Version @Column(name="version", nullable=false) private long version;
}
