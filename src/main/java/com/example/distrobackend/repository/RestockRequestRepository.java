package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.RestockRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RestockRequestRepository extends JpaRepository<RestockRequest, UUID> {

    Page<RestockRequest> findByDistributorOrganization_IdOrderByCreatedAtDesc(
            UUID organizationId, Pageable pageable);

    Page<RestockRequest> findByManufacturerOrganization_IdOrderByCreatedAtDesc(
            UUID organizationId, Pageable pageable);

    java.util.Optional<RestockRequest> findByIdAndDistributorOrganization_Id(
            UUID id, UUID organizationId);

    java.util.Optional<RestockRequest> findByIdAndManufacturerOrganization_Id(
            UUID id, UUID organizationId);
}
