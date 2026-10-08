package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
    Page<Warehouse> findByOrganization_IdAndActiveTrue(UUID organizationId, Pageable pageable);

    Optional<Warehouse> findByIdAndOrganization_Id(UUID id, UUID organizationId);

    boolean existsByOrganization_IdAndCodeIgnoreCase(UUID organizationId, String code);

    Optional<Warehouse> findByIdAndOrganization_IdAndActiveTrue(UUID id, UUID organizationId);
}
