package com.example.distrobackend.repository;
import com.example.distrobackend.Domain.entity.SupplierSettlement;
import com.example.distrobackend.Domain.enums.SupplierSettlementStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SupplierSettlementRepository extends JpaRepository<SupplierSettlement, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SupplierSettlement s where s.provider = com.example.distrobackend.Domain.enums.PaymentProvider.PAYSTACK "
            + "and s.status = com.example.distrobackend.Domain.enums.SupplierSettlementStatus.PROCESSING "
            + "and (s.nextReconciliationAt is null or s.nextReconciliationAt <= :now) order by s.lastAttemptAt asc")
    List<SupplierSettlement> lockDuePaystackReconciliation(@Param("now") java.time.OffsetDateTime now,
                                                           org.springframework.data.domain.Pageable pageable);
    List<SupplierSettlement> findBySupplierOrganization_IdOrderByCreatedAtDesc(UUID supplierId);
    Optional<SupplierSettlement> findByAllocation_Id(UUID allocationId);
    Page<SupplierSettlement> findByStatusOrderByCreatedAtAsc(SupplierSettlementStatus status, Pageable pageable);
    List<SupplierSettlement> findBySettlementBatch_IdOrderByCreatedAtAsc(UUID batchId);
    List<SupplierSettlement> findByAllocation_Payment_Id(UUID paymentId);
    List<SupplierSettlement> findByAllocation_Payment_IdIn(java.util.Collection<UUID> paymentIds);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SupplierSettlement s where s.allocation.payment.id = :paymentId")
    List<SupplierSettlement> findByPaymentForUpdate(@Param("paymentId") UUID paymentId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SupplierSettlement s where s.id = :id")
    Optional<SupplierSettlement> findByIdForUpdate(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SupplierSettlement s where s.payoutReference = :reference")
    Optional<SupplierSettlement> findByPayoutReferenceForUpdate(@Param("reference") String reference);
}
