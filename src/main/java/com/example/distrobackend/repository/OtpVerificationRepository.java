package com.example.distrobackend.repository;


import com.example.distrobackend.Domain.entity.OtpVerification;
import com.example.distrobackend.Domain.enums.OtpPurpose;
import com.example.distrobackend.Domain.enums.OtpStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OtpVerificationRepository extends JpaRepository<OtpVerification, UUID> {

    Optional<OtpVerification> findFirstByUser_IdAndPurposeOrderByCreatedAtDesc(UUID userId, OtpPurpose purpose);

    List<OtpVerification> findByUser_IdAndPurposeAndStatus(UUID userId, OtpPurpose purpose, OtpStatus status);
}
