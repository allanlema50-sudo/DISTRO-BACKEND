package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.PurchaseOrder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Page<PurchaseOrder> findByBuyerOrganizationId(UUID organizationId, Pageable pageable);

    Page<PurchaseOrder> findBySupplierOrganizationId(UUID organizationId, Pageable pageable);

    @Query("select p from PurchaseOrder p "
            + "join fetch p.buyerOrganization buyer "
            + "join fetch p.supplierOrganization supplier "
            + "join fetch p.createdBy creator "
            + "left join fetch p.reviewedBy reviewer "
            + "left join fetch p.items i "
            + "left join fetch i.stockItem "
            + "where p.id = :id")
    Optional<PurchaseOrder> findByIdWithItems(@Param("id") UUID id);

    @Query(value = "select nextval('purchase_order_number_seq')", nativeQuery = true)
    Long getNextPurchaseOrderSequence();
}
