package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    Optional<Organization> findByEmailIgnoreCase(String email);

    Optional<Organization> findByEmailIgnoreCaseAndIdNot(String email, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select organization from Organization organization where organization.id = :id")
    Optional<Organization> findByIdForUpdate(@Param("id") UUID id);
}
