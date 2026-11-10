package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.StockLocation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StockLocationRepository extends JpaRepository<StockLocation, UUID> {
    @Query("select coalesce(sum(location.quantity), 0) from StockLocation location "
            + "where location.stockItem.id = :stockItemId")
    long sumQuantityByStockItemId(@Param("stockItemId") UUID stockItemId);

    List<StockLocation> findByStockItem_IdAndStockItem_Organization_IdOrderByLocationCodeAsc(
            UUID stockItemId, UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select location from StockLocation location where location.stockItem.id = :stockItemId "
            + "and location.locationCode = :locationCode")
    Optional<StockLocation> findForUpdate(@Param("stockItemId") UUID stockItemId,
                                          @Param("locationCode") String locationCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select location from StockLocation location where location.stockItem.id = :stockItemId "
            + "order by location.locationCode asc")
    List<StockLocation> findAllForUpdate(@Param("stockItemId") UUID stockItemId);
}
