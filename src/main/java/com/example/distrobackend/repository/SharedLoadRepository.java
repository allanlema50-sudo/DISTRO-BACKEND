package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.SharedLoad;
import com.example.distrobackend.Domain.enums.SharedLoadStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface SharedLoadRepository extends JpaRepository<SharedLoad, UUID> {
    boolean existsByProposedTrip_IdAndCandidateTrip_IdAndStatusIn(
            UUID proposedTripId, UUID candidateTripId, Collection<SharedLoadStatus> statuses);

    @EntityGraph(attributePaths = {"proposedTrip", "candidateTrip", "proposerOrganization", "candidateOrganization"})
    @Query(value = "select s from SharedLoad s where (:organizationId is null "
            + "or s.proposerOrganization.id = :organizationId or s.candidateOrganization.id = :organizationId) "
            + "and (:status is null or s.status = :status)",
            countQuery = "select count(s) from SharedLoad s where (:organizationId is null "
                    + "or s.proposerOrganization.id = :organizationId or s.candidateOrganization.id = :organizationId) "
                    + "and (:status is null or s.status = :status)")
    Page<SharedLoad> search(@Param("organizationId") UUID organizationId,
                            @Param("status") SharedLoadStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"proposedTrip", "candidateTrip", "proposerOrganization", "candidateOrganization",
            "proposedBy", "reviewedBy", "confirmations", "confirmations.organization", "confirmations.confirmedBy"})
    @Query("select s from SharedLoad s where s.id = :id")
    Optional<SharedLoad> findWithDetailsById(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"proposedTrip", "candidateTrip", "proposerOrganization", "candidateOrganization", "confirmations"})
    @Query("select s from SharedLoad s where s.id = :id")
    Optional<SharedLoad> findByIdForUpdate(@Param("id") UUID id);
}
