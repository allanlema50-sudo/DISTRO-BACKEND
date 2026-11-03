package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.Shipment;
import com.example.distrobackend.Domain.enums.ShipmentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ShipmentRepository extends JpaRepository<Shipment, UUID> {
    boolean existsByOrder_Id(UUID orderId);
    Optional<Shipment> findByOrder_Id(UUID orderId);

    @EntityGraph(attributePaths = {"order", "organization", "trip", "createdBy"})
    @Query(value = "select s from Shipment s where (:organizationId is null or s.organization.id = :organizationId) "
            + "and (:customerId is null or s.order.customer.id = :customerId) "
            + "and (:status is null or s.status = :status)",
            countQuery = "select count(s) from Shipment s where (:organizationId is null or s.organization.id = :organizationId) "
                    + "and (:customerId is null or s.order.customer.id = :customerId) "
                    + "and (:status is null or s.status = :status)")
    Page<Shipment> search(@Param("organizationId") UUID organizationId,
                          @Param("customerId") UUID customerId,
                          @Param("status") ShipmentStatus status,
                          Pageable pageable);

    @EntityGraph(attributePaths = {"order", "order.customer", "organization", "trip", "createdBy", "statusHistory", "statusHistory.changedBy"})
    @Query("select s from Shipment s where s.id = :id")
    Optional<Shipment> findWithDetailsById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"order", "order.customer", "organization", "trip"})
    @Query("select s from Shipment s where s.id = :id")
    Optional<Shipment> findByIdForUpdate(@Param("id") UUID id);
}
