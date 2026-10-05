package com.example.distrobackend.Domain.entity;

import com.example.distrobackend.Domain.enums.RestockRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
@Table(name = "restock_requests")
@Getter
@Setter
@NoArgsConstructor
public class RestockRequest {

    @Id
    @GeneratedValue
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "distributor_organization_id", nullable = false)
    private Organization distributorOrganization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manufacturer_organization_id", nullable = false)
    private Organization manufacturerOrganization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stock_item_id", nullable = false)
    private StockItem stockItem;

    @Column(name = "requested_quantity", nullable = false)
    private int requestedQuantity;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RestockRequestStatus status = RestockRequestStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requested_by", nullable = false)
    private User requestedBy;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
