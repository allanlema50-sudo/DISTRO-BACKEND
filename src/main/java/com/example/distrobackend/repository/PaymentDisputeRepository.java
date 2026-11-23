package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.PaymentDispute;
import com.example.distrobackend.Domain.enums.PaymentDisputeStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentDisputeRepository extends JpaRepository<PaymentDispute, UUID> {
    Page<PaymentDispute> findAllByOrderByLastEventAtDesc(Pageable pageable);

    Page<PaymentDispute> findByStatusOrderByDueAtAscLastEventAtDesc(
            PaymentDisputeStatus status, Pageable pageable);

    Optional<PaymentDispute> findByProviderDisputeId(String providerDisputeId);

    java.util.List<PaymentDispute> findByPayment_IdIn(java.util.Collection<UUID> paymentIds);

    boolean existsByPayment_IdAndClearedAtIsNull(UUID paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PaymentDispute d where d.providerDisputeId = :providerDisputeId")
    Optional<PaymentDispute> findByProviderDisputeIdForUpdate(
            @Param("providerDisputeId") String providerDisputeId);
}
