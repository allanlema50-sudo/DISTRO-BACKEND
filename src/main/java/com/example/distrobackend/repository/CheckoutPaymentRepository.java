package com.example.distrobackend.repository;
import com.example.distrobackend.Domain.entity.CheckoutPayment;
import com.example.distrobackend.Domain.enums.CheckoutPaymentStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
 Optional<CheckoutPayment> findByProviderReference(String providerReference);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select p from CheckoutPayment p where p.providerReference = :providerReference")
 Optional<CheckoutPayment> findByProviderReferenceForUpdate(
         @org.springframework.data.repository.query.Param("providerReference") String providerReference);

 @Query("select p.id from CheckoutPayment p where p.provider = com.example.distrobackend.Domain.enums.PaymentProvider.PAYSTACK "
         + "and p.status in (com.example.distrobackend.Domain.enums.CheckoutPaymentStatus.PENDING, "
         + "com.example.distrobackend.Domain.enums.CheckoutPaymentStatus.UNKNOWN) "
         + "and p.providerNextVerificationAt <= :now order by p.providerNextVerificationAt, p.id")
 Page<UUID> findPaystackPaymentsDueForVerification(@Param("now") OffsetDateTime now, Pageable pageable);
}
