package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.AccessRequest;
import com.example.distrobackend.Domain.enums.AccessRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, UUID> {
    List<AccessRequest> findAllByOrderByCreatedAtDesc();

    List<AccessRequest> findByStatusOrderByCreatedAtDesc(AccessRequestStatus status);

    boolean existsByPersonalEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByPersonalEmailAndStatus(String email, AccessRequestStatus status);

    Optional<AccessRequest> findByPersonalEmailAndStatus(String email, AccessRequestStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from AccessRequest request where request.id = :id")
    Optional<AccessRequest> findByIdForUpdate(@Param("id") UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from AccessRequest request where request.personalEmail = :email and request.status = :status")
    Optional<AccessRequest> findByPersonalEmailAndStatusForUpdate(
            @Param("email") String email,
            @Param("status") AccessRequestStatus status
    );
}
