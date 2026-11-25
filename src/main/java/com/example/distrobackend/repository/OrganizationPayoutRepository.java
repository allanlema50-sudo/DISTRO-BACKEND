package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.OrganizationPayout;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrganizationPayoutRepository extends JpaRepository<OrganizationPayout, UUID> {
    Optional<OrganizationPayout> findByIdempotencyKey(String idempotencyKey);
}
