package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.Warehouse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.util.UUID;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {
    Page<Warehouse> findByOrganization_Id(UUID organizationId, Pageable pageable);

    Page<Warehouse> findByOrganization_IdAndActiveTrue(UUID organizationId, Pageable pageable);

    Optional<Warehouse> findByIdAndOrganization_Id(UUID id, UUID organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Warehouse w where w.id = :id and w.organization.id = :organizationId")
    Optional<Warehouse> findByIdAndOrganization_IdForUpdate(
            @Param("id") UUID id, @Param("organizationId") UUID organizationId);

    boolean existsByOrganization_IdAndCodeIgnoreCase(UUID organizationId, String code);

    Optional<Warehouse> findByIdAndOrganization_IdAndActiveTrue(UUID id, UUID organizationId);
}
