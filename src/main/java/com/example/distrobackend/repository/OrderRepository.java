package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.Order;
import com.example.distrobackend.Domain.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.OffsetDateTime;
import java.util.List;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

    long countByStatus(OrderStatus status);

    long countByOrganizationId(UUID organizationId);

    long countByOrganizationIdAndStatus(
            UUID organizationId,
            OrderStatus status
    );

    long countByStatusNotIn(
            Collection<OrderStatus> statuses
    );

    long countByOrganizationIdAndStatusNotIn(
            UUID organizationId,
            Collection<OrderStatus> statuses
    );

    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0)
            FROM Order o
            """)
    BigDecimal sumTotalAmount();

    @Query("""
            SELECT COALESCE(SUM(o.totalAmount), 0)
            FROM Order o
            WHERE o.organization.id = :organizationId
            """)
    BigDecimal sumTotalAmountByOrganizationId(
            @Param("organizationId") UUID organizationId
    );

    @Query("""
            SELECT o
            FROM Order o
            JOIN FETCH o.customer c
            JOIN FETCH o.organization org
            WHERE
                LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(org.name) LIKE LOWER(CONCAT('%', :search, '%'))
            """)
    Page<Order> searchAllOrders(
            @Param("search") String search,
            Pageable pageable
    );

    @Query("""
            SELECT o
            FROM Order o
            JOIN FETCH o.customer c
            JOIN FETCH o.organization org
            WHERE o.organization.id = :organizationId
              AND (
                    LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%'))
                    OR LOWER(org.name) LIKE LOWER(CONCAT('%', :search, '%'))
              )
            """)
    Page<Order> searchOrganizationOrders(
            @Param("organizationId") UUID organizationId,
            @Param("search") String search,
            Pageable pageable
    );

        List<Order> findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
        OffsetDateTime startDate
    );
    
    @Query("""
            SELECT o
            FROM Order o
            LEFT JOIN FETCH o.orderItems
            WHERE o.id = :id
            """)
    Optional<Order> findByIdWithItems(
            @Param("id") UUID id
    );

    @Query("""
            SELECT o
            FROM Order o
            JOIN FETCH o.customer
            JOIN FETCH o.organization
            WHERE o.id = :id
            """)
    Optional<Order> findByIdWithOwnership(
            @Param("id") UUID id
    );

    @Query(
            value = """
                    SELECT o
                    FROM Order o
                    JOIN FETCH o.customer c
                    JOIN FETCH o.organization org
                    WHERE o.customer.id = :customerId
                    """,
            countQuery = """
                    SELECT COUNT(o)
                    FROM Order o
                    WHERE o.customer.id = :customerId
                    """
    )
    Page<Order> findByCustomerIdWithDetails(
            @Param("customerId") UUID customerId,
            Pageable pageable
    );

    @Query(
            value = """
                    SELECT o
                    FROM Order o
                    JOIN FETCH o.customer c
                    JOIN FETCH o.organization org
                    WHERE o.organization.id = :organizationId
                    """,
            countQuery = """
                    SELECT COUNT(o)
                    FROM Order o
                    WHERE o.organization.id = :organizationId
                    """
    )
    Page<Order> findByOrganizationIdWithDetails(
            @Param("organizationId") UUID organizationId,
            Pageable pageable
    );

    @Query(value = "SELECT nextval('order_number_seq')", nativeQuery = true)
    Long getNextOrderSequence();
}
