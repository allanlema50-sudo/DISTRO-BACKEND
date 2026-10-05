package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.AccessRequest;
import com.example.distrobackend.Domain.enums.AccessRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface AccessRequestRepository extends JpaRepository<AccessRequest, UUID> {
    List<AccessRequest> findAllByOrderByCreatedAtDesc();
    boolean existsByPersonalEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);
    boolean existsByPersonalEmailAndStatus(String email, AccessRequestStatus status);
    Optional<AccessRequest> findByPersonalEmailAndStatus(String email, AccessRequestStatus status);
}
