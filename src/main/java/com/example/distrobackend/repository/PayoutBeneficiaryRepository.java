package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.PayoutBeneficiary;
import com.example.distrobackend.Domain.enums.PayoutBeneficiaryStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PayoutBeneficiaryRepository extends JpaRepository<PayoutBeneficiary, UUID> {
    Page<PayoutBeneficiary> findByOrganization_IdOrderByCreatedAtDesc(UUID organizationId, Pageable pageable);
    Optional<PayoutBeneficiary> findByOrganization_IdAndStatusAndCurrency(UUID organizationId,
                                                                            PayoutBeneficiaryStatus status,
                                                                            String currency);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from PayoutBeneficiary b where b.id = :id and b.organization.id = :organizationId")
    Optional<PayoutBeneficiary> findByIdAndOrganizationForUpdate(@Param("id") UUID id,
                                                                  @Param("organizationId") UUID organizationId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from PayoutBeneficiary b where b.id = :id")
    Optional<PayoutBeneficiary> findByIdForUpdate(@Param("id") UUID id);
    Optional<PayoutBeneficiary> findByProviderRecipientCode(String providerRecipientCode);
}
