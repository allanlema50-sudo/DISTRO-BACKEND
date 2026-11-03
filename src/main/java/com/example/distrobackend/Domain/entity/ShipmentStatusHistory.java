package com.example.distrobackend.Domain.entity;

import com.example.distrobackend.Domain.enums.ShipmentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "shipment_status_history", indexes = @Index(name = "idx_shipment_status_history", columnList = "shipment_id,created_at"))
@Getter @Setter @NoArgsConstructor
public class ShipmentStatusHistory {
    @Id @GeneratedValue @JdbcTypeCode(SqlTypes.UUID)
    private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private Shipment shipment;
    @Enumerated(EnumType.STRING) @Column(name = "from_status", length = 20)
    private ShipmentStatus fromStatus;
    @Enumerated(EnumType.STRING) @Column(name = "to_status", nullable = false, length = 20)
    private ShipmentStatus toStatus;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;
    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
