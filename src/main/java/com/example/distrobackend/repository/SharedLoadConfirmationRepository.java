package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.SharedLoadConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface SharedLoadConfirmationRepository extends JpaRepository<SharedLoadConfirmation, UUID> {
    Optional<SharedLoadConfirmation> findBySharedLoad_IdAndOrganization_Id(UUID sharedLoadId, UUID organizationId);
}
