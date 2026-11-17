package com.example.distrobackend.Domain.entity;
import com.example.distrobackend.Domain.enums.CheckoutStatus;
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
import java.util.UUID;
@Entity
@Table(name="checkouts", indexes=@Index(name="idx_checkouts_buyer_created", columnList="buyer_organization_id,created_at"))
@Getter @Setter @NoArgsConstructor
public class Checkout {
 @Id @GeneratedValue @JdbcTypeCode(SqlTypes.UUID) private UUID id;
 @Column(name="checkout_reference", nullable=false, unique=true, length=40) private String checkoutReference;
 @Column(name="idempotency_key", length=120) private String idempotencyKey;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="buyer_organization_id") private Organization buyerOrganization;
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="buyer_user_id") private User buyerUser;
 @Column(name="buyer_type", nullable=false, length=32) private String buyerType;
 @OneToOne(mappedBy="checkout", fetch=FetchType.LAZY) private CheckoutPayment payment;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="created_by", nullable=false) private User createdBy;
 @Enumerated(EnumType.STRING) @Column(name="status", nullable=false, length=24) private CheckoutStatus status=CheckoutStatus.PAYMENT_PENDING;
 @Column(name="total_amount", nullable=false, precision=14, scale=2) private BigDecimal totalAmount;
 @Column(name="currency", nullable=false, length=3) private String currency="KES";
 @CreationTimestamp @Column(name="created_at", nullable=false, updatable=false) private OffsetDateTime createdAt;
 @UpdateTimestamp @Column(name="updated_at", nullable=false) private OffsetDateTime updatedAt;
 @Version @Column(name="version", nullable=false) private long version;
}
