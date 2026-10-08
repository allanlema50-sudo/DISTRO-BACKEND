package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {
    Optional<Payment> findByOrderId(UUID orderId);
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
}
