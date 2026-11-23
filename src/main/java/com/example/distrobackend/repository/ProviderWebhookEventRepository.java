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
    boolean existsByEventKey(String eventKey);

    @Modifying
    @Query(value = "UPDATE provider_webhook_events SET status = 'RETRY', next_attempt_at = CURRENT_TIMESTAMP, "
            + "last_error = 'Recovered after worker interruption', updated_at = CURRENT_TIMESTAMP "
            + "WHERE status = 'PROCESSING' AND updated_at < :cutoff", nativeQuery = true)
    int recoverStaleProcessing(@Param("cutoff") OffsetDateTime cutoff);

    Page<ProviderWebhookEvent> findAllByOrderByReceivedAtDesc(Pageable pageable);

    Page<ProviderWebhookEvent> findByStatusOrderByReceivedAtAsc(ProviderWebhookStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ProviderWebhookEvent e where e.id = :id")
    java.util.Optional<ProviderWebhookEvent> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from ProviderWebhookEvent e where e.status in :statuses and e.nextAttemptAt <= :now "
            + "order by e.receivedAt asc")
    List<ProviderWebhookEvent> lockDue(@Param("statuses") java.util.Set<ProviderWebhookStatus> statuses,
                                       @Param("now") OffsetDateTime now, Pageable pageable);
}
