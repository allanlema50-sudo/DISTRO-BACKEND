package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StockMovementRepository extends JpaRepository<StockMovement, UUID> {

    Page<StockMovement> findByStockItem_IdAndStockItem_Organization_IdOrderByCreatedAtDesc(
            UUID stockItemId, UUID organizationId, Pageable pageable);
}
