package com.example.distrobackend.Domain.entity;

import com.example.distrobackend.Domain.enums.ShipmentStatus;
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
@Table(name = "shipments", indexes = @Index(name = "idx_shipments_org_status", columnList = "organization_id,status"))
@Getter
@Setter
@NoArgsConstructor
public class Shipment {
    @Id @GeneratedValue @JdbcTypeCode(SqlTypes.UUID)
    private UUID id;

    @Column(name = "shipment_number", nullable = false, unique = true, length = 40)
    private String shipmentNumber;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trip_id")
    private Trip trip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ShipmentStatus status = ShipmentStatus.READY;

    @Column(name = "tracking_reference", length = 100)
    private String trackingReference;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "shipment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<ShipmentStatusHistory> statusHistory = new ArrayList<>();

    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Version @Column(name = "version", nullable = false)
    private long version;

    public void addStatusHistory(ShipmentStatusHistory history) {
        statusHistory.add(history);
        history.setShipment(this);
    }
}
