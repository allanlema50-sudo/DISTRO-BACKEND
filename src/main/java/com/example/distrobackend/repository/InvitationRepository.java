package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.Invitation;
import com.example.distrobackend.Domain.enums.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByToken(String token);

    List<Invitation> findByOrganizationId(UUID organizationId);

    List<Invitation> findByOrganizationIdAndStatus(
            UUID organizationId,
            InvitationStatus status
    );

    boolean existsByToken(String token);
}