package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.ProviderWebhookEvent;
import com.example.distrobackend.Domain.enums.ProviderWebhookStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface ProviderWebhookEventRepository extends JpaRepository<ProviderWebhookEvent, UUID> {
    @Modifying
    @Query(value = "INSERT INTO provider_webhook_events (id, provider, event_key, event_name, provider_reference, "
            + "provider_status, amount_minor, currency, gateway_response, transfer_code, status, attempt_count, "
            + "claim_token, next_attempt_at, received_at, updated_at, version) "
            + "VALUES (:id, :provider, :eventKey, :eventName, :providerReference, :providerStatus, :amountMinor, "
            + ":currency, :gatewayResponse, :transferCode, 'RECEIVED', 0, NULL, CURRENT_TIMESTAMP, "
            + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0) ON CONFLICT (event_key) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id, @Param("provider") String provider,
                       @Param("eventKey") String eventKey, @Param("eventName") String eventName,
                       @Param("providerReference") String providerReference,
                       @Param("providerStatus") String providerStatus, @Param("amountMinor") Long amountMinor,
                       @Param("currency") String currency, @Param("gatewayResponse") String gatewayResponse,
                       @Param("transferCode") String transferCode);

    @Modifying
    @Query(value = "UPDATE provider_webhook_events SET status = CASE WHEN attempt_count >= :maxAttempts "
            + "THEN 'DEAD_LETTER' ELSE 'RETRY' END, next_attempt_at = CURRENT_TIMESTAMP, claim_token = NULL, "
            + "last_error = 'Recovered after worker interruption', updated_at = CURRENT_TIMESTAMP "
            + "WHERE status = 'PROCESSING' AND updated_at < :cutoff", nativeQuery = true)
    int recoverStaleProcessing(@Param("cutoff") OffsetDateTime cutoff,
                               @Param("maxAttempts") int maxAttempts);

    @Modifying
    @Query(value = "UPDATE provider_webhook_events SET status = 'DEAD_LETTER', claim_token = NULL, "
            + "last_error = 'Maximum processing attempts reached', updated_at = CURRENT_TIMESTAMP "
            + "WHERE status IN ('RECEIVED', 'RETRY') AND attempt_count >= :maxAttempts", nativeQuery = true)
    int deadLetterExhausted(@Param("maxAttempts") int maxAttempts);

    Page<ProviderWebhookEvent> findAllByOrderByReceivedAtDesc(Pageable pageable);

    Page<ProviderWebhookEvent> findByStatusOrderByReceivedAtAsc(ProviderWebhookStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ProviderWebhookEvent e where e.id = :id")
    java.util.Optional<ProviderWebhookEvent> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ProviderWebhookEvent e where e.status in :statuses and e.nextAttemptAt <= :now "
            + "and e.attemptCount < :maxAttempts order by e.receivedAt asc")
    List<ProviderWebhookEvent> lockDue(@Param("statuses") java.util.Set<ProviderWebhookStatus> statuses,
                                       @Param("now") OffsetDateTime now,
                                       @Param("maxAttempts") int maxAttempts, Pageable pageable);
}
