package com.example.distrobackend.repository;
import com.example.distrobackend.Domain.entity.CheckoutPayment;
import com.example.distrobackend.Domain.enums.CheckoutPaymentStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CheckoutPaymentRepository extends JpaRepository<CheckoutPayment, UUID> {
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select p from CheckoutPayment p where p.id = :id")
 Optional<CheckoutPayment> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);
 Optional<CheckoutPayment> findByCheckout_Id(UUID checkoutId);
 Optional<CheckoutPayment> findFirstByCheckout_IdOrderByInitiatedAtDesc(UUID checkoutId);
 List<CheckoutPayment> findByStatusAndInitiatedAtBefore(CheckoutPaymentStatus status, OffsetDateTime before);
 List<CheckoutPayment> findByDemoTrueAndStatusAndInitiatedAtBefore(CheckoutPaymentStatus status, OffsetDateTime before);
 Optional<CheckoutPayment> findByIdAndCheckout_Id(UUID paymentId, UUID checkoutId);
 Optional<CheckoutPayment> findByMpesaCheckoutRequestId(String checkoutRequestId);
 Optional<CheckoutPayment> findByIdempotencyKey(String idempotencyKey);
}
