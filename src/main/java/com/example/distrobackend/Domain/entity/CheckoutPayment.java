package com.example.distrobackend.Domain.entity;
import com.example.distrobackend.Domain.enums.PaymentMethod;
import com.example.distrobackend.Domain.enums.CheckoutPaymentStatus;
import com.example.distrobackend.Domain.enums.PaymentProvider;
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
import java.util.Map;
import java.util.UUID;
@Entity @Table(name="checkout_payments")
@Getter @Setter @NoArgsConstructor
public class CheckoutPayment {
 @Id @GeneratedValue @JdbcTypeCode(SqlTypes.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="checkout_id", nullable=false) private Checkout checkout;
 @Enumerated(EnumType.STRING) @Column(name="method", nullable=false) private PaymentMethod method;
 @Enumerated(EnumType.STRING) @Column(name="status", nullable=false) private CheckoutPaymentStatus status=CheckoutPaymentStatus.PENDING;
 @Enumerated(EnumType.STRING) @Column(name="provider", length=20) private PaymentProvider provider;
 @Column(name="amount", nullable=false, precision=14, scale=2) private BigDecimal amount;
 @Column(name="currency", nullable=false, length=3) private String currency="KES";
 @Column(name="provider_reference", unique=true, length=120) private String providerReference;
 @Column(name="provider_authorization_url", columnDefinition="TEXT") private String providerAuthorizationUrl;
 @Column(name="provider_access_code", length=120) private String providerAccessCode;
 @Column(name="demo", nullable=false) private boolean demo;
 @Column(name="demo_label", length=80) private String demoLabel;
 @Column(name="idempotency_key", unique=true, length=120) private String idempotencyKey;
 @Column(name="mpesa_checkout_request_id", unique=true, length=100) private String mpesaCheckoutRequestId;
 @Column(name="mpesa_merchant_request_id", length=100) private String mpesaMerchantRequestId;
 @Column(name="failure_reason", columnDefinition="TEXT") private String failureReason;
 @JdbcTypeCode(SqlTypes.JSON) @Column(name="provider_payload", columnDefinition="jsonb") private Map<String,Object> providerPayload;
 @Column(name="initiated_at", nullable=false) private OffsetDateTime initiatedAt;
 @Column(name="next_reconciliation_at") private OffsetDateTime nextReconciliationAt;
 @Column(name="reconciliation_attempts", nullable=false) private int reconciliationAttempts;
 @Column(name="confirmed_at") private OffsetDateTime confirmedAt;
 @CreationTimestamp @Column(name="created_at", nullable=false, updatable=false) private OffsetDateTime createdAt;
 @UpdateTimestamp @Column(name="updated_at", nullable=false) private OffsetDateTime updatedAt;
 @Version @Column(name="version", nullable=false) private long version;
}
