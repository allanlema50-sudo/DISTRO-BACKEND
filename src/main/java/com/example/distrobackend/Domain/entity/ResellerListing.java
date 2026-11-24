package com.example.distrobackend.Domain.entity;

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
@Table(name = "reseller_listings", uniqueConstraints = @UniqueConstraint(
        name = "uk_reseller_listing_seller_product", columnNames = {"distributor_organization_id", "manufacturer_product_id"}))
@Getter @Setter @NoArgsConstructor
public class ResellerListing {
    @Id @GeneratedValue @JdbcTypeCode(SqlTypes.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "distributor_organization_id", nullable = false) private Organization distributor;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "manufacturer_product_id", nullable = false) private StockItem manufacturerProduct;
    @Column(name = "retail_price", nullable = false, precision = 12, scale = 2) private BigDecimal retailPrice;
    @Column(name = "listing_status", nullable = false, length = 20) private String status = "ACTIVE";
    @Column(name = "quantity_on_hand", nullable = false) private int quantityOnHand;
    @Column(name = "reserved_quantity", nullable = false) private int reservedQuantity;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    public int getAvailableQuantity() { return quantityOnHand - reservedQuantity; }
}
