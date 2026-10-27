package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.DistributorProductAuthorization;
import com.example.distrobackend.Domain.enums.ProductAuthorizationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;

public interface DistributorProductAuthorizationRepository
        extends JpaRepository<DistributorProductAuthorization, UUID> {

    Page<DistributorProductAuthorization> findByDistributorOrganization_IdOrderByCreatedAtDesc(
            UUID distributorOrganizationId, Pageable pageable);

    Page<DistributorProductAuthorization> findByManufacturerOrganization_IdOrderByCreatedAtDesc(
            UUID manufacturerOrganizationId, Pageable pageable);

    Optional<DistributorProductAuthorization> findByProduct_IdAndDistributorOrganization_Id(
            UUID productId, UUID distributorOrganizationId);

    boolean existsByProduct_IdAndDistributorOrganization_IdAndStatus(
            UUID productId, UUID distributorOrganizationId, ProductAuthorizationStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from DistributorProductAuthorization a "
            + "where a.id = :id and a.manufacturerOrganization.id = :organizationId")
    Optional<DistributorProductAuthorization> findByIdAndManufacturerOrganizationForUpdate(
            @Param("id") UUID id, @Param("organizationId") UUID organizationId);
}
