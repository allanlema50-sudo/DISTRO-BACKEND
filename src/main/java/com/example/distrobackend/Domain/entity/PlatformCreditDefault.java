package com.example.distrobackend.Domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Fallback credit terms used only when a relationship has no row of its own. */
@Entity
@Table(name = "platform_credit_defaults")
@Getter
@Setter
@NoArgsConstructor
public class PlatformCreditDefault {

    @Id
    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "default_limit", nullable = false, precision = 14, scale = 2)
    private BigDecimal defaultLimit;

    @Column(name = "default_term_days", nullable = false)
    private int defaultTermDays;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

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
