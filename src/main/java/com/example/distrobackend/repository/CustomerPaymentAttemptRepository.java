package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.CustomerPaymentAttempt;
import com.example.distrobackend.Domain.enums.CustomerPaymentAttemptStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface CustomerPaymentAttemptRepository extends JpaRepository<CustomerPaymentAttempt, UUID> {
    Optional<CustomerPaymentAttempt> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CustomerPaymentAttempt a where a.id = :id")
    Optional<CustomerPaymentAttempt> findByIdForUpdate(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"payment", "payment.order", "payment.order.customer", "payment.order.organization"})
    Optional<CustomerPaymentAttempt> findByProviderReference(String providerReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from CustomerPaymentAttempt a where a.providerReference = :reference")
    Optional<CustomerPaymentAttempt> findByProviderReferenceForUpdate(@Param("reference") String reference);

    Optional<CustomerPaymentAttempt> findFirstByPayment_IdOrderByAttemptNumberDesc(UUID paymentId);

    boolean existsByPayment_IdAndStatusIn(UUID paymentId,
            Collection<CustomerPaymentAttemptStatus> statuses);

    @Query("select a.id from CustomerPaymentAttempt a "
            + "where a.status in (com.example.distrobackend.Domain.enums.CustomerPaymentAttemptStatus.INITIALIZING, "
            + "com.example.distrobackend.Domain.enums.CustomerPaymentAttemptStatus.PENDING, "
            + "com.example.distrobackend.Domain.enums.CustomerPaymentAttemptStatus.OUTCOME_UNKNOWN) "
            + "and a.nextVerificationAt <= :now order by a.nextVerificationAt, a.id")
    Page<UUID> findDueForVerification(@Param("now") OffsetDateTime now, Pageable pageable);
}
