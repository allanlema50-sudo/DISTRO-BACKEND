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
@Entity @Table(name="checkout_items", indexes=@Index(name="idx_checkout_items_checkout", columnList="checkout_id"))
@Getter @Setter @NoArgsConstructor
public class CheckoutItem {
 @Id @GeneratedValue @JdbcTypeCode(SqlTypes.UUID) private UUID id;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="checkout_id", nullable=false) private Checkout checkout;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="purchase_order_id", nullable=false) private PurchaseOrder purchaseOrder;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="supplier_organization_id", nullable=false) private Organization supplierOrganization;
 @Column(name="product_id", nullable=false) @JdbcTypeCode(SqlTypes.UUID) private UUID productId;
 @Column(name="supplier_name_snapshot", nullable=false, length=150) private String supplierNameSnapshot;
 @Column(name="product_name_snapshot", nullable=false, length=200) private String productNameSnapshot;
 @Column(name="quantity", nullable=false) private int quantity;
 @Column(name="unit_price", nullable=false, precision=14, scale=2) private BigDecimal unitPrice;
 @Column(name="line_total", nullable=false, precision=14, scale=2) private BigDecimal lineTotal;
 @Column(name="currency", nullable=false, length=3) private String currency="KES";
 @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="destination_warehouse_id") private Warehouse destinationWarehouse;
 @CreationTimestamp @Column(name="created_at", nullable=false, updatable=false) private OffsetDateTime createdAt;
}
