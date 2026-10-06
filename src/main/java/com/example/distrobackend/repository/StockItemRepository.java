package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.enums.ProductApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

public interface StockItemRepository extends JpaRepository<StockItem, UUID> {

    List<StockItem> findAllByOrganizationId(UUID organizationId);

    Optional<StockItem> findByIdAndOrganizationId(UUID id, UUID organizationId);

    long countByActiveTrue();

    long countByApprovalStatus(ProductApprovalStatus approvalStatus);

    long countByQuantityOnHandLessThanEqualAndActiveTrue(int quantity);
}
