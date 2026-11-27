package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.PlatformFeeSetting;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PlatformFeeSettingRepository extends JpaRepository<PlatformFeeSetting, UUID> {
    Optional<PlatformFeeSetting> findByScope(String scope);
}
