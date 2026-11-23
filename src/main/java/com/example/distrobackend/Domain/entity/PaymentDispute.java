package com.example.distrobackend.Domain.entity;

import com.example.distrobackend.Domain.enums.PaymentDisputeStatus;
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
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payment_disputes")
@Getter
@Setter
@NoArgsConstructor
public class PaymentDispute {
    @Id
    @GeneratedValue
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID id;

    @Column(name = "provider_dispute_id", nullable = false, unique = true, length = 80)
    private String providerDisputeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "checkout_payment_id")
    private CheckoutPayment payment;

    @Column(name = "transaction_reference", length = 120)
    private String transactionReference;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 40)
    private PaymentDisputeStatus status = PaymentDisputeStatus.UNKNOWN;

    @Column(name = "resolution", length = 80)
    private String resolution;

    @Column(name = "disputed_amount", precision = 14, scale = 2)
    private BigDecimal disputedAmount;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "due_at")
    private OffsetDateTime dueAt;

    @Column(name = "last_event_name", nullable = false, length = 80)
    private String lastEventName;

    @Column(name = "last_event_at", nullable = false)
    private OffsetDateTime lastEventAt;

    @Column(name = "reminder_count", nullable = false)
    private int reminderCount;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cleared_by")
    private User clearedBy;

    @Column(name = "cleared_at")
    private OffsetDateTime clearedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
