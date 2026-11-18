package com.example.distrobackend.repository;
import com.example.distrobackend.Domain.entity.Checkout;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface CheckoutRepository extends JpaRepository<Checkout, UUID> {
 Page<Checkout> findByBuyerOrganization_IdOrderByCreatedAtDesc(UUID buyerOrganizationId, Pageable pageable);
 Optional<Checkout> findByIdAndBuyerOrganization_Id(UUID id, UUID buyerOrganizationId);
 @Lock(LockModeType.PESSIMISTIC_WRITE)
 @Query("select c from Checkout c where c.id = :checkoutId and c.buyerOrganization.id = :buyerOrganizationId")
 Optional<Checkout> findByIdAndBuyerOrganization_IdForUpdate(@Param("checkoutId") UUID checkoutId,
                                                               @Param("buyerOrganizationId") UUID buyerOrganizationId);
 Optional<Checkout> findByBuyerOrganization_IdAndIdempotencyKey(UUID buyerOrganizationId, String idempotencyKey);
}
