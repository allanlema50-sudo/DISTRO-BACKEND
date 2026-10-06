package com.example.distrobackend.Domain.entity;

import com.example.distrobackend.Domain.enums.StopStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "trip_stops")
@Getter
@Setter
@NoArgsConstructor
public class TripStop {

    @Id
    @GeneratedValue
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    @Column(name = "sequence_num", nullable = false)
    private Integer sequence;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private StopStatus status = StopStatus.PENDING;

    @Column(name = "location_name")
    private String locationName;

    @Column(name = "location_address")
    private String locationAddress;

    @Column(name = "location_lat")
    private Double locationLat;

    @Column(name = "location_lng")
    private Double locationLng;

    @Column(name = "eta_label")
    private String etaLabel;

    @Column(name = "distance_from_prev_km")
    private Integer distanceFromPreviousKm;

    @Column(name = "contact_name")
    private String contactName;

    @Column(name = "contact_phone")
    private String contactPhone;

    // A stop might be for a specific order (delivery)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id")
    private Order order;

    // Or it might be for a restock intake
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "stock_item_id")
    private StockItem stockItem;

    @Column(name = "restock_quantity")
    private Integer restockQuantity;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
