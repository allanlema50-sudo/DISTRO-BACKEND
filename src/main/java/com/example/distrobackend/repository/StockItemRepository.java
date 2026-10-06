package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.enums.ProductApprovalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface StockItemRepository extends JpaRepository<StockItem, UUID> {

    long countByActiveTrue();

    long countByApprovalStatus(ProductApprovalStatus approvalStatus);

    long countByQuantityOnHandLessThanEqualAndActiveTrue(int quantity);

    long countByOrganizationId(UUID organizationId);

    long countByOrganizationIdAndActiveTrue(UUID organizationId);

    long countByOrganizationIdAndApprovalStatus(
            UUID organizationId,
            ProductApprovalStatus approvalStatus
    );

    long countByOrganizationIdAndQuantityOnHandLessThanEqualAndActiveTrue(
            UUID organizationId,
            int quantity
    );

    @Query("""
        SELECT COALESCE(SUM(s.quantityOnHand), 0)
        FROM StockItem s
        WHERE s.organizationId = :organizationId
        """)
    long sumQuantityOnHandByOrganizationId(
            @Param("organizationId") UUID organizationId
    );
}
