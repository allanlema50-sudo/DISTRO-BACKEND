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
 @org.springframework.data.jpa.repository.Query(value = "select * from checkout_payments p "
         + "where p.provider = 'PAYSTACK' and p.status = 'PENDING' "
         + "and p.initiated_at <= :cutoff "
         + "and (p.provider_status_checked_at is null or p.provider_status_checked_at <= :checkBefore) "
         + "and p.provider_payload ->> 'state' = 'REQUEST_OUTCOME_UNKNOWN' "
         + "order by p.initiated_at asc limit :batchSize", nativeQuery = true)
 List<CheckoutPayment> findUnknownPaystackAttempts(
         @org.springframework.data.repository.query.Param("cutoff") OffsetDateTime cutoff,
         @org.springframework.data.repository.query.Param("checkBefore") OffsetDateTime checkBefore,
         @org.springframework.data.repository.query.Param("batchSize") int batchSize);
 List<CheckoutPayment> findByDemoTrueAndStatusAndInitiatedAtBefore(CheckoutPaymentStatus status, OffsetDateTime before);
 Optional<CheckoutPayment> findByIdAndCheckout_Id(UUID paymentId, UUID checkoutId);
 Optional<CheckoutPayment> findByMpesaCheckoutRequestId(String checkoutRequestId);
 Optional<CheckoutPayment> findByIdempotencyKey(String idempotencyKey);
 Optional<CheckoutPayment> findByProviderReference(String providerReference);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select p from CheckoutPayment p where p.providerReference = :providerReference")
 Optional<CheckoutPayment> findByProviderReferenceForUpdate(
         @org.springframework.data.repository.query.Param("providerReference") String providerReference);
}
