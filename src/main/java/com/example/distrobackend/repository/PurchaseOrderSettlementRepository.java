package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.PurchaseOrderSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.UUID;

public interface PurchaseOrderSettlementRepository extends JpaRepository<PurchaseOrderSettlement, UUID> {
    List<PurchaseOrderSettlement> findByPurchaseOrderIdOrderByCreatedAtDesc(UUID purchaseOrderId);
    java.util.Optional<PurchaseOrderSettlement> findByIdempotencyKey(String idempotencyKey);
    java.util.Optional<PurchaseOrderSettlement> findByMpesaCheckoutRequestId(String checkoutRequestId);
    java.util.Optional<PurchaseOrderSettlement> findFirstByPurchaseOrder_IdOrderByCreatedAtDesc(UUID purchaseOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from PurchaseOrderSettlement s join fetch s.purchaseOrder p "
            + "join fetch p.buyerOrganization buyer join fetch p.supplierOrganization supplier "
            + "where s.id = :id")
    java.util.Optional<PurchaseOrderSettlement> findByIdForUpdate(UUID id);
}
