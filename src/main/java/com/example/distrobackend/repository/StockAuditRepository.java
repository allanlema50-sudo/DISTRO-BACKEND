package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.StockAudit;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface StockAuditRepository extends JpaRepository<StockAudit, UUID> {

    Page<StockAudit> findByOrganization_IdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);

    @Query("select a from StockAudit a "
            + "join fetch a.organization org join fetch a.warehouse warehouse "
            + "join fetch a.stockItem item join fetch a.recordedBy recorded "
            + "left join fetch a.appliedBy applied "
            + "where a.id = :id and a.organization.id = :organizationId")
    Optional<StockAudit> findByIdWithDetails(@Param("id") UUID id,
                                             @Param("organizationId") UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from StockAudit a where a.id = :id and a.organization.id = :organizationId")
    Optional<StockAudit> findByIdAndOrganizationIdForUpdate(
            @Param("id") UUID id, @Param("organizationId") UUID organizationId);
}
