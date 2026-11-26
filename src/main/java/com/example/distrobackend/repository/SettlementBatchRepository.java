package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.SettlementBatch;
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

public interface SettlementBatchRepository extends JpaRepository<SettlementBatch, UUID> {
    Page<SettlementBatch> findAllByOrderByCreatedAtDesc(Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from SettlementBatch b where b.id = :id")
    Optional<SettlementBatch> findByIdForUpdate(@Param("id") UUID id);
    @Query(value = "select id from settlement_batches "
            + "where execution_mode = 'TEST_AUTOMATIC' and provider_mode = 'TEST' and status = 'APPROVED' "
            + "order by created_at asc limit :batchSize", nativeQuery = true)
    List<UUID> findApprovedAutomaticTestBatchIds(@Param("batchSize") int batchSize);
}
