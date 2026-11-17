package com.example.distrobackend.Domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "organization_notification_preferences")
@Getter
@Setter
@NoArgsConstructor
public class OrganizationNotificationPreferences {
    @Id
    @JdbcTypeCode(SqlTypes.UUID)
    @Column(name = "organization_id")
    private UUID organizationId;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @Column(name = "dispatch_exceptions_sms", nullable = false)
    private boolean dispatchExceptionsSms;

    @Column(name = "purchase_order_approvals_email", nullable = false)
    private boolean purchaseOrderApprovalsEmail;

    @Column(name = "weekly_capacity_forecasts", nullable = false)
    private boolean weeklyCapacityForecasts;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
