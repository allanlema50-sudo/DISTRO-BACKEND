package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.orderItems WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") UUID id);

    @Query("SELECT o FROM Order o JOIN FETCH o.customer JOIN FETCH o.organization WHERE o.id = :id")
    Optional<Order> findByIdWithOwnership(@Param("id") UUID id);

    @Query(value = "SELECT o FROM Order o JOIN FETCH o.customer c JOIN FETCH o.organization org WHERE o.customer.id = :customerId",
           countQuery = "SELECT COUNT(o) FROM Order o WHERE o.customer.id = :customerId")
    Page<Order> findByCustomerIdWithDetails(@Param("customerId") UUID customerId, Pageable pageable);

    @Query(value = "SELECT o FROM Order o JOIN FETCH o.customer c JOIN FETCH o.organization org WHERE o.organization.id = :organizationId",
           countQuery = "SELECT COUNT(o) FROM Order o WHERE o.organization.id = :organizationId")
    Page<Order> findByOrganizationIdWithDetails(@Param("organizationId") UUID organizationId, Pageable pageable);

    @Query(value = "SELECT nextval('order_number_seq')", nativeQuery = true)
    Long getNextOrderSequence();
}
