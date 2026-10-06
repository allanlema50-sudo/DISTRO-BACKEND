package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    Optional<User> findByPhoneNumber(String phoneNumber);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    List<User> findByStatus(UserStatus status);

    List<User> findByRole(UserRole role);

    List<User> findByOrganizationId(UUID organizationId);

    List<User> findByOrganizationIdAndStatus(
            UUID organizationId,
            UserStatus status
    );

    List<User> findByOrganizationIdAndRole(
            UUID organizationId,
            UserRole role
    );

    Optional<User> findByIdAndOrganizationId(
            UUID id,
            UUID organizationId
    );

    long countByOrganizationId(UUID organizationId);
}
