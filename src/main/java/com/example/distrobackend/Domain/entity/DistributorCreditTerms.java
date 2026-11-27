package com.example.distrobackend.Domain.entity;

import com.example.distrobackend.Domain.enums.CreditTermStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Credit a manufacturer extends to one distributor. Unique per pair. */
@Entity
@Table(name = "distributor_credit_terms", uniqueConstraints = @UniqueConstraint(
        name = "uk_distributor_credit_terms_pair",
        columnNames = {"manufacturer_organization_id", "distributor_organization_id"}))
@Getter
@Setter
@NoArgsConstructor
public class DistributorCreditTerms {

    @Id
    @GeneratedValue
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manufacturer_organization_id", nullable = false)
    private Organization manufacturerOrganization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "distributor_organization_id", nullable = false)
    private Organization distributorOrganization;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "KES";

    @Column(name = "credit_limit", nullable = false, precision = 14, scale = 2)
    private BigDecimal creditLimit;

    @Column(name = "term_days", nullable = false)
    private int termDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private CreditTermStatus status = CreditTermStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "set_by", nullable = false)
    private User setBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt = OffsetDateTime.now();

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @PreUpdate
    void touch() {
        updatedAt = OffsetDateTime.now();
    }
}
