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
@Table(name = "stock_items", indexes = {
        @Index(name = "idx_stock_items_organization", columnList = "organization_id"),
        @Index(name = "idx_stock_items_organization_active", columnList = "organization_id,is_active"),
        @Index(name = "idx_stock_items_source_stock_item", columnList = "source_stock_item_id"),
        @Index(name = "idx_stock_items_warehouse_active", columnList = "warehouse_id,is_active")
})
@Getter
@Setter
@NoArgsConstructor


public class StockItem {
    @Id
    @GeneratedValue
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    /**
     * Manufacturer-owned source item for a distributor offer. Null means this
     * row is a manufacturer source item rather than a distributor listing.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_stock_item_id")
    private StockItem sourceStockItem;

    /** Warehouse holding the physical quantity for a distributor listing. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "warehouse_id")
    private Warehouse warehouse;

    // SKU uniqueness is tenant-scoped in Flyway, including case-insensitive matching.
    @Column(name = "sku", nullable = false, length = 50)
    private String sku;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "category", length = 80)
    private String category;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "quantity_on_hand", nullable = false)
    private int quantityOnHand = 0;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity = 0;

    @Column(name = "reorder_threshold", nullable = false)
    private int reorderThreshold = 0;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    public int getAvailableQuantity() {
        return quantityOnHand - reservedQuantity;
    }

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
