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
import java.time.OffsetDateTime;

public interface SupplierSettlementRepository extends JpaRepository<SupplierSettlement, UUID> {
    List<SupplierSettlement> findBySupplierOrganization_IdOrderByCreatedAtDesc(UUID supplierId);
    Optional<SupplierSettlement> findByAllocation_Id(UUID allocationId);
    Page<SupplierSettlement> findByStatusOrderByCreatedAtAsc(SupplierSettlementStatus status, Pageable pageable);
    List<SupplierSettlement> findBySettlementBatch_IdOrderByCreatedAtAsc(UUID batchId);
    List<SupplierSettlement> findByAllocation_Payment_Id(UUID paymentId);
    List<SupplierSettlement> findByAllocation_Payment_IdIn(java.util.Collection<UUID> paymentIds);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SupplierSettlement s where s.allocation.payment.id = :paymentId")
    List<SupplierSettlement> findByPaymentForUpdate(@Param("paymentId") UUID paymentId);
    Optional<SupplierSettlement> findByPayoutReference(String reference);
    @Query(value = "select * from supplier_settlements s "
            + "where s.provider = 'PAYSTACK' and s.status = 'PROCESSING' "
            + "and s.last_attempt_at <= :attemptCutoff "
            + "and (s.last_status_check_at is null or s.last_status_check_at <= :checkBefore) "
            + "order by coalesce(s.last_status_check_at, s.last_attempt_at) asc limit :batchSize",
            nativeQuery = true)
    List<SupplierSettlement> findPaystackTransfersNeedingVerification(
            @Param("attemptCutoff") OffsetDateTime attemptCutoff,
            @Param("checkBefore") OffsetDateTime checkBefore,
            @Param("batchSize") int batchSize);
    @Query(value = "select s.id from supplier_settlements s "
            + "join settlement_batches b on b.id = s.settlement_batch_id "
            + "where s.provider = 'PAYSTACK' and s.status = 'PENDING' and b.status = 'PROCESSING' "
            + "order by s.created_at asc limit :batchSize", nativeQuery = true)
    List<UUID> findPendingPaystackSettlementsInProcessingBatches(@Param("batchSize") int batchSize);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SupplierSettlement s where s.settlementBatch.id = :batchId order by s.createdAt asc")
    List<SupplierSettlement> findBySettlementBatchIdForUpdate(@Param("batchId") UUID batchId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SupplierSettlement s where s.id = :id")
    Optional<SupplierSettlement> findByIdForUpdate(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SupplierSettlement s where s.payoutReference = :reference")
    Optional<SupplierSettlement> findByPayoutReferenceForUpdate(@Param("reference") String reference);
}
