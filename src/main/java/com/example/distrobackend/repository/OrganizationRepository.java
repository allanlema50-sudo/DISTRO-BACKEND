package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.OrganizationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;
import java.util.Optional;
import java.util.List;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    Page<Organization> findAllByType(Organizationtype type, Pageable pageable);
    List<Organization> findAllByTypeAndStatusOrderByNameAsc(
            Organizationtype type, OrganizationStatus status);
    Optional<Organization> findByEmailIgnoreCase(String email);

    Optional<Organization> findByEmailIgnoreCaseAndIdNot(String email, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select organization from Organization organization where organization.id = :id")
    Optional<Organization> findByIdForUpdate(@Param("id") UUID id);
}
