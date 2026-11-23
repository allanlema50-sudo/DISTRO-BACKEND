package com.example.distrobackend.Domain.entity;

import com.example.distrobackend.Domain.enums.ProviderWebhookStatus;
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
@Table(name = "provider_webhook_events", indexes = {
        @Index(name = "idx_provider_webhook_status_next_attempt", columnList = "status,next_attempt_at,received_at")
})
@Getter
@Setter
@NoArgsConstructor
public class ProviderWebhookEvent {
    @Id
    @GeneratedValue
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID id;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider = "PAYSTACK";

    @Column(name = "event_key", nullable = false, unique = true, length = 64)
    private String eventKey;

    @Column(name = "event_name", nullable = false, length = 80)
    private String eventName;

    @Column(name = "provider_reference", nullable = false, length = 120)
    private String providerReference;

    @Column(name = "provider_status", length = 40)
    private String providerStatus;

    @Column(name = "amount_minor")
    private Long amountMinor;

    @Column(name = "currency", length = 3)
    private String currency;

    @Column(name = "gateway_response", columnDefinition = "TEXT")
    private String gatewayResponse;

    @Column(name = "transfer_code", length = 120)
    private String transferCode;

    @Column(name = "provider_dispute_id", length = 80)
    private String providerDisputeId;

    @Column(name = "dispute_status", length = 40)
    private String disputeStatus;

    @Column(name = "dispute_resolution", length = 80)
    private String disputeResolution;

    @Column(name = "dispute_due_at")
    private OffsetDateTime disputeDueAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProviderWebhookStatus status = ProviderWebhookStatus.RECEIVED;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private OffsetDateTime nextAttemptAt = OffsetDateTime.now();

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    @CreationTimestamp
    @Column(name = "received_at", nullable = false, updatable = false)
    private OffsetDateTime receivedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;
}
