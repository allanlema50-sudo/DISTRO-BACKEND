
package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.Payment;
import com.example.distrobackend.Domain.enums.PaymentMethod;
import com.example.distrobackend.Domain.enums.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import java.time.OffsetDateTime;
import java.util.List;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByOrderId(UUID orderId);

    Optional<Payment> findByMpesaCheckoutRequestId(
            String mpesaCheckoutRequestId
    );

    long countByOrder_Organization_Id(UUID organizationId);

    long countByStatus(PaymentStatus status);

    long countByOrder_Organization_IdAndStatus(
            UUID organizationId,
            PaymentStatus status
    );

    @EntityGraph(attributePaths = {
            "order",
            "order.customer",
            "order.organization"
    })
    @Query(
            value = """
                    SELECT p
                    FROM Payment p
                    JOIN p.order o
                    LEFT JOIN o.customer c
                    LEFT JOIN o.organization org
                    WHERE (
                        :search = ''
                        OR LOWER(COALESCE(o.orderNumber, ''))
                            LIKE LOWER(CONCAT('%', :search, '%'))
                        OR LOWER(COALESCE(c.fullName, ''))
                            LIKE LOWER(CONCAT('%', :search, '%'))
                        OR LOWER(COALESCE(org.name, ''))
                            LIKE LOWER(CONCAT('%', :search, '%'))
                        OR LOWER(COALESCE(p.mpesaReceiptNumber, ''))
                            LIKE LOWER(CONCAT('%', :search, '%'))
                    )
                    AND (:status IS NULL OR p.status = :status)
                    AND (:method IS NULL OR p.method = :method)
                    """,
            countQuery = """
                    SELECT COUNT(p)
                    FROM Payment p
                    JOIN p.order o
                    LEFT JOIN o.customer c
                    LEFT JOIN o.organization org
                    WHERE (
                        :search = ''
                        OR LOWER(COALESCE(o.orderNumber, ''))
                            LIKE LOWER(CONCAT('%', :search, '%'))
                        OR LOWER(COALESCE(c.fullName, ''))
                            LIKE LOWER(CONCAT('%', :search, '%'))
                        OR LOWER(COALESCE(org.name, ''))
                            LIKE LOWER(CONCAT('%', :search, '%'))
                        OR LOWER(COALESCE(p.mpesaReceiptNumber, ''))
                            LIKE LOWER(CONCAT('%', :search, '%'))
                    )
                    AND (:status IS NULL OR p.status = :status)
                    AND (:method IS NULL OR p.method = :method)
                    """
    )
    Page<Payment> findAdminPayments(
            @Param("search") String search,
            @Param("status") PaymentStatus status,
            @Param("method") PaymentMethod method,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {
            "order",
            "order.customer",
            "order.organization"
    })
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findDetailedById(@Param("id") UUID id);

    @Query("""
    SELECT COALESCE(SUM(p.amount), 0)
    FROM Payment p
    WHERE p.status = :status
    """)
BigDecimal sumAmountByStatus(
        @Param("status") PaymentStatus status
);
List<Payment> findByStatusAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
        PaymentStatus status,
        OffsetDateTime startDate
);
}
