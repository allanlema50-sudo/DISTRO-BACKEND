package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.PurchaseOrderSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PurchaseOrderSettlementRepository extends JpaRepository<PurchaseOrderSettlement, UUID> {
    List<PurchaseOrderSettlement> findByPurchaseOrderIdOrderByCreatedAtDesc(UUID purchaseOrderId);
}
