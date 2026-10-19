package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.Payment;
import com.example.distrobackend.Domain.enums.PaymentStatus;
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

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOrderId(UUID orderId);

    @EntityGraph(attributePaths = {"order", "order.customer", "order.organization"})
    @Query(value = "select p from Payment p join fetch p.order o join fetch o.customer c join fetch o.organization org",
            countQuery = "select count(p) from Payment p join p.order o join o.customer c join o.organization org")
    Page<Payment> findAllWithOwnership(Pageable pageable);

    @EntityGraph(attributePaths = {"order", "order.customer", "order.organization"})
    Page<Payment> findByOrder_Customer_Id(UUID customerId, Pageable pageable);

    @EntityGraph(attributePaths = {"order", "order.customer", "order.organization"})
    Page<Payment> findByOrder_Organization_Id(UUID organizationId, Pageable pageable);

    long countByOrder_Organization_Id(UUID organizationId);

    long countByStatus(PaymentStatus status);

    @Query("select coalesce(sum(p.amount), 0) from Payment p where p.status = :status")
    java.math.BigDecimal sumAmountByStatus(@Param("status") PaymentStatus status);

    @EntityGraph(attributePaths = {"order", "order.customer", "order.organization"})
    @Query("select p from Payment p join fetch p.order o join fetch o.customer c join fetch o.organization org "
            + "where p.id = :paymentId")
    Optional<Payment> findByIdWithOwnership(UUID paymentId);

    @EntityGraph(attributePaths = {"order", "order.customer", "order.organization"})
    @Query("select p from Payment p join fetch p.order o join fetch o.customer c join fetch o.organization org "
            + "where o.id = :orderId")
    Optional<Payment> findByOrderIdWithOwnership(UUID orderId);

    @Query("select p.order.id from Payment p where p.id = :paymentId")
    Optional<UUID> findOrderIdByPaymentId(@Param("paymentId") UUID paymentId);
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p join fetch p.order o join fetch o.customer c join fetch o.organization org where o.id = :orderId")
    Optional<Payment> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    @Query("select p.id, o.id from Payment p join p.order o "
            + "where p.mpesaCheckoutRequestId = :checkoutRequestId")
    Optional<Object[]> findPaymentAndOrderIdsByMpesaCheckoutRequestId(
            @Param("checkoutRequestId") String checkoutRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p join fetch p.order o join fetch o.customer c join fetch o.organization org "
            + "where p.id = :paymentId")
    Optional<Payment> findByIdForUpdate(@Param("paymentId") UUID paymentId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p join fetch p.order o join fetch o.customer c join fetch o.organization org "
            + "where p.id = :paymentId")
    Optional<Payment> findByIdWithOwnershipForUpdate(@Param("paymentId") UUID paymentId);
}
