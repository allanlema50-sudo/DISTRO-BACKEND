package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.StockItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockItemRepository extends JpaRepository<StockItem, UUID> {
    List<StockItem> findByOrganizationIdOrderByNameAsc(UUID organizationId);
    List<StockItem> findByOrganizationIdAndActiveTrueOrderByNameAsc(UUID organizationId);
    Optional<StockItem> findByIdAndOrganizationId(UUID id, UUID organizationId);
    boolean existsByOrganizationIdAndSku(UUID organizationId, String sku);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockItem s where s.id = :id and s.organization.id = :organizationId")
    Optional<StockItem> findByIdAndOrganizationIdForUpdate(
            @Param("id") UUID id, @Param("organizationId") UUID organizationId);
}
