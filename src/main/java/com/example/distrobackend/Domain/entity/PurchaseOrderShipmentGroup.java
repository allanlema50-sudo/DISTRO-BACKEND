package com.example.distrobackend.Domain.entity;

import com.example.distrobackend.Domain.enums.ManufacturerShipmentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "purchase_order_shipment_groups")
@Getter
@Setter
@NoArgsConstructor
public class PurchaseOrderShipmentGroup {
    @Id
    @GeneratedValue
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID id;

    @Column(name = "shipment_reference", nullable = false, unique = true, length = 40)
    private String shipmentReference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manufacturer_organization_id", nullable = false)
    private Organization manufacturerOrganization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_warehouse_id", nullable = false)
    private Warehouse originWarehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_warehouse_id", nullable = false)
    private Warehouse destinationWarehouse;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ManufacturerShipmentStatus status = ManufacturerShipmentStatus.CREATED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @ManyToMany
    @JoinTable(name = "purchase_order_shipment_group_items",
            joinColumns = @JoinColumn(name = "shipment_group_id"),
            inverseJoinColumns = @JoinColumn(name = "purchase_order_shipment_id"),
            uniqueConstraints = @UniqueConstraint(name = "uk_purchase_order_shipment_group_consignment",
                    columnNames = "purchase_order_shipment_id"))
    private List<PurchaseOrderShipment> shipments = new ArrayList<>();

    @Column(name = "dispatched_at")
    private OffsetDateTime dispatchedAt;

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
