package com.example.distrobackend.repository;
import com.example.distrobackend.Domain.entity.CheckoutPayment;
import com.example.distrobackend.Domain.enums.CheckoutPaymentStatus;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CheckoutPaymentRepository extends JpaRepository<CheckoutPayment, UUID> {
 Optional<CheckoutPayment> findByCheckout_Id(UUID checkoutId);
 List<CheckoutPayment> findByStatusAndInitiatedAtBefore(CheckoutPaymentStatus status, OffsetDateTime before);
 Optional<CheckoutPayment> findByIdAndCheckout_Id(UUID paymentId, UUID checkoutId);
}
