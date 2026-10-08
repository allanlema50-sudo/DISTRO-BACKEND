package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    Page<StockMovement> findByStockItem_IdAndStockItem_Organization_IdOrderByCreatedAtDesc(
            UUID stockItemId, UUID organizationId, Pageable pageable);
}
