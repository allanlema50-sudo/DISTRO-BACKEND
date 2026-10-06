package com.example.distrobackend.Domain.entity;
import com.example.distrobackend.Domain.enums.StockMovementType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.CreationTimestamp;

import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_movements")
@Getter
@Setter
@NoArgsConstructor



public class StockMovement {   @Id
@GeneratedValue
@JdbcTypeCode(SqlTypes.UUID)
private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_item_id", nullable = false)
    private StockItem stockItem;

    @Enumerated(EnumType.STRING)
    @Column(name = "movement_type", nullable = false)
    private StockMovementType movementType;

    @Column(name = "quantity_delta", nullable = false)
    private int quantityDelta; // positive = in, negative = out

    // polymorphic pointer to the source of the movement (order, trip, manual, ...)
    @Column(name = "reference_type", length = 50)
    private String referenceType;

    @Column(name = "reference_id")
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID referenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by")
    private User performedBy;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}

