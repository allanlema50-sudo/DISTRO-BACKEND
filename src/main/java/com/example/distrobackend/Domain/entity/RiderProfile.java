package com.example.distrobackend.Domain.entity;

import jakarta.persistence.Table;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "rider_profiles")
@Getter
@Setter
@NoArgsConstructor
public class RiderProfile {
@Id
@Column(name = "user_id")
@JdbcTypeCode(SqlTypes.UUID)
private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "vehicle_type", length = 50)
    private String vehicleType;

    @Column(name = "vehicle_reg_number", length = 30)
    private String vehicleRegNumber;

    @Column(name = "license_number", length = 50)
    private String licenseNumber;

    @Column(name = "is_available", nullable = false)
    private boolean available = false;

    @Column(name = "current_lat")
    private Double currentLat;

    @Column(name = "current_lng")
    private Double currentLng;

    @Column(name = "last_location_at")
    private OffsetDateTime lastLocationAt;
}
