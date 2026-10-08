package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.OrganizationNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrganizationNotificationRepository
        extends JpaRepository<OrganizationNotification, UUID> {

    Page<OrganizationNotification> findByOrganization_IdOrderByCreatedAtDesc(
            UUID organizationId, Pageable pageable);
}
