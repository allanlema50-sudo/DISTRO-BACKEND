package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface OrganizationRepository extends JpaRepository<Organization, UUID> {
    Optional<Organization> findByEmailIgnoreCase(String email);
}
