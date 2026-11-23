package com.example.distrobackend.repository;
import com.example.distrobackend.Domain.entity.CheckoutPayment;
import com.example.distrobackend.Domain.enums.CheckoutPaymentStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface CheckoutPaymentRepository extends JpaRepository<CheckoutPayment, UUID> {
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select p from CheckoutPayment p where p.provider = com.example.distrobackend.Domain.enums.PaymentProvider.PAYSTACK "
         + "and p.status = com.example.distrobackend.Domain.enums.CheckoutPaymentStatus.PENDING "
         + "and (p.nextReconciliationAt is null or p.nextReconciliationAt <= :now) order by p.initiatedAt asc")
 List<CheckoutPayment> lockDuePaystackReconciliation(@Param("now") OffsetDateTime now, Pageable pageable);
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
 java.util.List<CheckoutPayment> findByProviderAndProviderReferenceIn(
         com.example.distrobackend.Domain.enums.PaymentProvider provider,
         java.util.Collection<String> providerReferences);
 @org.springframework.data.jpa.repository.EntityGraph(attributePaths = {"checkout"})
 org.springframework.data.domain.Page<CheckoutPayment> findByProviderAndDemoFalseAndStatusInAndInitiatedAtGreaterThanEqualAndInitiatedAtLessThanOrderByInitiatedAtDesc(
         com.example.distrobackend.Domain.enums.PaymentProvider provider,
         java.util.Collection<CheckoutPaymentStatus> statuses,
         OffsetDateTime fromInclusive, OffsetDateTime toExclusive, org.springframework.data.domain.Pageable pageable);
 @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
 @org.springframework.data.jpa.repository.Query("select p from CheckoutPayment p where p.providerReference = :providerReference")
 Optional<CheckoutPayment> findByProviderReferenceForUpdate(
         @org.springframework.data.repository.query.Param("providerReference") String providerReference);
}
