package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.StockItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockItemRepository extends JpaRepository<StockItem, UUID> {
    Page<StockItem> findByOrganization_Id(UUID organizationId, Pageable pageable);

    Page<StockItem> findByActiveTrue(Pageable pageable);

    Page<StockItem> findByActiveTrueAndCategoryIgnoreCase(String category, Pageable pageable);

    Page<StockItem> findByOrganization_IdAndActiveTrue(UUID organizationId, Pageable pageable);

    @Query("select distinct s.category from StockItem s where s.active = true and s.category is not null order by s.category")
    java.util.List<String> findActiveCategories();

    Optional<StockItem> findByIdAndOrganization_Id(UUID id, UUID organizationId);

    default Optional<StockItem> findByIdAndOrganizationId(UUID id, UUID organizationId) {
        return findByIdAndOrganization_Id(id, organizationId);
    }

    boolean existsByOrganization_IdAndSkuIgnoreCase(UUID organizationId, String sku);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockItem s where s.id = :id and s.organization.id = :organizationId")
    Optional<StockItem> findByIdAndOrganizationIdForUpdate(
            @Param("id") UUID id, @Param("organizationId") UUID organizationId);
    @Query("""
            select s from StockItem s
            where s.organization.id = :organizationId
              and s.active = true
              and s.quantityOnHand <= s.reorderThreshold
            """)
    Page<StockItem> findLowStockByOrganizationId(
            @Param("organizationId") UUID organizationId, Pageable pageable);
}
