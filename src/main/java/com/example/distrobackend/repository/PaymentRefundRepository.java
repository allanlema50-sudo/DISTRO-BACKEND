package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.PaymentRefund;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.util.List;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, UUID> {
    Optional<PaymentRefund> findTopByPayment_IdOrderByCreatedAtDescIdDesc(UUID paymentId);
    Optional<PaymentRefund> findByProviderReference(String reference);
    Page<PaymentRefund> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PaymentRefund r where r.id = :id")
    Optional<PaymentRefund> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PaymentRefund r where r.status in (com.example.distrobackend.Domain.enums.RefundStatus.REQUESTED, "
            + "com.example.distrobackend.Domain.enums.RefundStatus.PENDING, "
            + "com.example.distrobackend.Domain.enums.RefundStatus.PROCESSING) "
            + "and (r.nextReconciliationAt is null or r.nextReconciliationAt <= :now) order by r.createdAt asc")
    List<PaymentRefund> lockDue(@Param("now") OffsetDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from PaymentRefund r where r.providerReference = :reference")
    Optional<PaymentRefund> findByProviderReferenceForUpdate(@Param("reference") String reference);
}
