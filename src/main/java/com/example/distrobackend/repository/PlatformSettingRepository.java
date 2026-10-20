package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.PlatformSetting;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSettingRepository extends JpaRepository<PlatformSetting, String> {}
