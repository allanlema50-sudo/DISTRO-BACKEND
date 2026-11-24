package com.example.distrobackend.Domain.entity;

import com.example.distrobackend.Domain.enums.FeePayer;
import com.example.distrobackend.Domain.enums.PlatformFeeType;
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
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** Super-admin configurable platform fee. One row per scope; ships at zero. */
@Entity
@Table(name = "platform_fee_settings")
@Getter
@Setter
@NoArgsConstructor
public class PlatformFeeSetting {

    public static final String PURCHASE_ORDER_SCOPE = "PURCHASE_ORDER";

    @Id
    @GeneratedValue
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID id;

    @Column(name = "scope", nullable = false, length = 30)
    private String scope = PURCHASE_ORDER_SCOPE;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false, length = 10)
    private PlatformFeeType feeType = PlatformFeeType.PERCENT;

    /** A percentage (0 to 100) for PERCENT, or a money amount for FLAT. */
    @Column(name = "fee_value", nullable = false, precision = 14, scale = 2)
    private BigDecimal feeValue = BigDecimal.ZERO.setScale(2);

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "KES";

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_payer", nullable = false, length = 10)
    private FeePayer feePayer = FeePayer.SUPPLIER;

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
