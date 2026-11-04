package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.OrganizationNotificationPreferences;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrganizationNotificationPreferencesRepository
        extends JpaRepository<OrganizationNotificationPreferences, UUID> {}
