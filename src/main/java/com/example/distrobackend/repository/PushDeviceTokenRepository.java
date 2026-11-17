package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.PushDeviceToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PushDeviceTokenRepository extends JpaRepository<PushDeviceToken, UUID> {
    Optional<PushDeviceToken> findByDeviceToken(String deviceToken);
    Optional<PushDeviceToken> findByDeviceTokenAndUser_Id(String deviceToken, UUID userId);
    void deleteAllByUser_Id(UUID userId);
}
