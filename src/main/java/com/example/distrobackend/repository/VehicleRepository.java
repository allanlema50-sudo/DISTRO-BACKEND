package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.Vehicle;
import com.example.distrobackend.Domain.enums.VehicleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VehicleRepository extends JpaRepository<Vehicle, UUID> {

    Page<Vehicle> findByOrganization_Id(UUID organizationId, Pageable pageable);

    Page<Vehicle> findByOrganization_IdAndStatus(
            UUID organizationId, VehicleStatus status, Pageable pageable);

    boolean existsByOrganization_IdAndRegistrationNumberIgnoreCase(
            UUID organizationId, String registrationNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from Vehicle v join fetch v.organization "
            + "where v.id = :vehicleId and v.organization.id = :organizationId")
    Optional<Vehicle> findByIdAndOrganizationForUpdate(
            @Param("vehicleId") UUID vehicleId,
            @Param("organizationId") UUID organizationId);

    @Query("select v from Vehicle v join fetch v.organization "
            + "where v.id = :vehicleId and v.organization.id = :organizationId")
    Optional<Vehicle> findByIdAndOrganization(
            @Param("vehicleId") UUID vehicleId,
            @Param("organizationId") UUID organizationId);
}
