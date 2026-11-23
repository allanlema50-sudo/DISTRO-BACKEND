package com.example.distrobackend.Domain.entity;

import com.example.distrobackend.Domain.enums.PaymentProvider;
import com.example.distrobackend.Domain.enums.PayoutBeneficiaryStatus;
import com.example.distrobackend.Domain.enums.PayoutMethod;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "payout_beneficiaries")
@Getter
@Setter
@NoArgsConstructor
public class PayoutBeneficiary {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 30)
    private PayoutMethod method;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 20)
    private PaymentProvider provider = PaymentProvider.PAYSTACK;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "KES";

    @Column(name = "account_name", nullable = false, length = 150)
    private String accountName;

    /** Only a masked value is retained; account details remain with Paystack. */
    @Column(name = "account_mask", nullable = false, length = 40)
    private String accountMask;

    @Column(name = "bank_code", length = 30)
    private String bankCode;

    @Column(name = "provider_recipient_code", nullable = false, unique = true, length = 100)
    private String providerRecipientCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PayoutBeneficiaryStatus status = PayoutBeneficiaryStatus.PENDING_REVIEW;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

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
