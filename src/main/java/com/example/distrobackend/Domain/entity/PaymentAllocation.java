package com.example.distrobackend.Domain.entity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;
@Entity
@Table(name="payment_allocations", uniqueConstraints=@UniqueConstraint(name="uk_payment_allocation_payment_order", columnNames={"payment_id","purchase_order_id"}))
@Getter @Setter @NoArgsConstructor
public class PaymentAllocation {
 @Id @GeneratedValue @JdbcTypeCode(SqlTypes.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="payment_id", nullable=false) private CheckoutPayment payment;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="purchase_order_id", nullable=false) private PurchaseOrder purchaseOrder;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="supplier_organization_id", nullable=false) private Organization supplierOrganization;
 @Column(name="gross_amount", nullable=false, precision=14, scale=2) private BigDecimal grossAmount;
 @Column(name="provider_fee", nullable=false, precision=14, scale=2) private BigDecimal providerFee=BigDecimal.ZERO;
 @Column(name="commission", nullable=false, precision=14, scale=2) private BigDecimal commission=BigDecimal.ZERO;
 @Column(name="supplier_amount", nullable=false, precision=14, scale=2) private BigDecimal supplierAmount;
 @Column(name="demo", nullable=false) private boolean demo;

 @Column(name="currency", nullable=false, length=3) private String currency="KES";
 @CreationTimestamp @Column(name="created_at", nullable=false, updatable=false) private OffsetDateTime createdAt;
}
