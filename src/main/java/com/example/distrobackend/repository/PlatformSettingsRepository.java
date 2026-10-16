package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.PlatformSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSettingsRepository
        extends JpaRepository<PlatformSettings, Long> {
}