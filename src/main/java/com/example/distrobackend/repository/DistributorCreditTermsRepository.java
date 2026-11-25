package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.DistributorCreditTerms;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface DistributorCreditTermsRepository extends JpaRepository<DistributorCreditTerms, UUID> {

    Optional<DistributorCreditTerms> findByManufacturerOrganization_IdAndDistributorOrganization_Id(
            UUID manufacturerId, UUID distributorId);

    /** Serializes concurrent credit purchase orders for the pair until the transaction ends. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from DistributorCreditTerms t where t.manufacturerOrganization.id = :manufacturerId "
            + "and t.distributorOrganization.id = :distributorId")
    Optional<DistributorCreditTerms> findForUpdate(@Param("manufacturerId") UUID manufacturerId,
                                                   @Param("distributorId") UUID distributorId);
}
