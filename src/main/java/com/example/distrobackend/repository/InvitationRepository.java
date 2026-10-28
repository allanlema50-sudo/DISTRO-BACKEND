package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.Invitation;
import com.example.distrobackend.Domain.enums.InvitationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvitationRepository
        extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByTokenHash(String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from Invitation invitation join fetch invitation.organization "
            + "left join fetch invitation.invitedUser "
            + "where invitation.tokenHash = :tokenHash")
    Optional<Invitation> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    List<Invitation> findByOrganizationId(UUID organizationId);

    List<Invitation> findAllByOrderByCreatedAtDesc();

    List<Invitation> findByOrganizationIdAndStatus(
            UUID organizationId,
            InvitationStatus status
    );

    boolean existsByEmailIgnoreCaseAndOrganizationIdAndStatus(
            String email,
            UUID organizationId,
            InvitationStatus status
    );

    Optional<Invitation> findTopByInvitedUser_IdOrderByCreatedAtDesc(UUID userId);
}
