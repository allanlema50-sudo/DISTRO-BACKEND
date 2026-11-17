package com.example.distrobackend.repository;
import com.example.distrobackend.Domain.entity.Checkout;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface CheckoutRepository extends JpaRepository<Checkout, UUID> {
 Page<Checkout> findByBuyerOrganization_IdOrderByCreatedAtDesc(UUID buyerOrganizationId, Pageable pageable);
 Optional<Checkout> findByIdAndBuyerOrganization_Id(UUID id, UUID buyerOrganizationId);
 Optional<Checkout> findByBuyerOrganization_IdAndIdempotencyKey(UUID buyerOrganizationId, String idempotencyKey);
}
