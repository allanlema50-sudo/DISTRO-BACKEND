package com.example.distrobackend.Domain.entity;

import com.example.distrobackend.Domain.enums.TripStatus;
import com.example.distrobackend.Domain.enums.TripType;
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
@Table(name = "trips")
@Getter
@Setter
@NoArgsConstructor
public class Trip { 
    @Id
    @GeneratedValue
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID id;

    @Column(name = "trip_number", nullable = false, unique = true)
    private String tripNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "trip_type", nullable = false)
    private TripType tripType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private TripStatus status = TripStatus.SCHEDULED;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rider_id")
    private User rider;

    @Column(name = "origin_name")
    private String originName;

    @Column(name = "origin_address")
    private String originAddress;

    @Column(name = "origin_lat")
    private Double originLat;

    @Column(name = "origin_lng")
    private Double originLng;

    @Column(name = "scheduled_start_label")
    private String scheduledStartLabel;

    @Column(name = "total_distance_km")
    private Integer totalDistanceKm;

    @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequence ASC")
    private List<TripStop> stops = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    
    public void addStop(TripStop stop) {
        stops.add(stop);
        stop.setTrip(this);
    }
}
