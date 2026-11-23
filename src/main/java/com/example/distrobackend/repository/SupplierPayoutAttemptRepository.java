package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.SupplierPayoutAttempt;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

public interface SupplierPayoutAttemptRepository extends JpaRepository<SupplierPayoutAttempt, UUID> {
    Page<SupplierPayoutAttempt> findBySettlement_IdOrderByAttemptNumberDesc(UUID settlementId,
                                                                             Pageable pageable);

    Optional<SupplierPayoutAttempt> findTopBySettlement_IdOrderByAttemptNumberDesc(UUID settlementId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from SupplierPayoutAttempt a join fetch a.settlement where a.providerReference = :reference")
    Optional<SupplierPayoutAttempt> findByProviderReferenceForUpdate(@Param("reference") String reference);
}
