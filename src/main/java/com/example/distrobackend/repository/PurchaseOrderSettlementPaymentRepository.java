package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.PurchaseOrderSettlementPayment;
import com.example.distrobackend.Domain.enums.SettlementPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderSettlementPaymentRepository
        extends JpaRepository<PurchaseOrderSettlementPayment, UUID> {

    Optional<PurchaseOrderSettlementPayment> findBySupplierOrganization_IdAndPayerReference(
            UUID supplierOrganizationId, String payerReference);

    Optional<PurchaseOrderSettlementPayment> findByIdempotencyKey(String idempotencyKey);

    List<PurchaseOrderSettlementPayment> findBySettlement_IdOrderBySubmittedAtAsc(UUID settlementId);

    List<PurchaseOrderSettlementPayment> findBySettlement_PurchaseOrder_IdOrderBySubmittedAtAsc(UUID purchaseOrderId);

    @Query("select coalesce(sum(p.amount), 0) from PurchaseOrderSettlementPayment p "
            + "where p.settlement.id = :settlementId and p.status = :status")
    BigDecimal sumAmountByStatus(@Param("settlementId") UUID settlementId,
                                 @Param("status") SettlementPaymentStatus status);

    boolean existsBySettlement_PurchaseOrder_Id(UUID purchaseOrderId);
}
