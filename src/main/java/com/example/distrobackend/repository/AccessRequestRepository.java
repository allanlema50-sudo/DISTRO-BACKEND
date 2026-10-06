package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.AccessRequest;
import com.example.distrobackend.Domain.enums.AccessRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AccessRequestRepository
        extends JpaRepository<AccessRequest, UUID> {

    List<AccessRequest>
    findAllByOrderByCreatedAtDesc();

    List<AccessRequest>
    findByStatusOrderByCreatedAtDesc(
            AccessRequestStatus status
    );
}