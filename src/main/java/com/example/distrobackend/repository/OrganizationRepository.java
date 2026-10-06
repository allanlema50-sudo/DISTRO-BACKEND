package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationRepository
        extends JpaRepository<Organization, UUID> {

    boolean existsByNameIgnoreCase(String name);

    Optional<Organization> findByNameIgnoreCase(String name);
}