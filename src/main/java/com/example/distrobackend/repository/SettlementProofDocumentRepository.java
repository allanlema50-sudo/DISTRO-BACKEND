package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.SettlementProofDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SettlementProofDocumentRepository extends JpaRepository<SettlementProofDocument, UUID> {
    Optional<SettlementProofDocument> findByIdAndPurchaseOrder_Id(UUID id, UUID purchaseOrderId);
}
