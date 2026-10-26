package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.UserRole;
import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository  extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByPhoneNumber(String phoneNumber);
    List<User> findByRole(UserRole role);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") UUID id);
    @EntityGraph(attributePaths = "organization")
    Optional<User> findWithOrganizationById(UUID id);
    @EntityGraph(attributePaths = "organization")
    @Query("select u from User u "
            + "where (:organizationId is null or u.organization.id = :organizationId) "
            + "and (:status is null or u.status = :status) "
            + "and (:role is null or u.role = :role)")
    Page<User> searchForAdministration(
            @Param("organizationId") UUID organizationId,
            @Param("status") com.example.distrobackend.Domain.enums.UserStatus status,
            @Param("role") com.example.distrobackend.Domain.enums.UserRole role,
            Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.organization.id = :organizationId and u.role in :roles")
    List<User> findOrganizationAdministratorsForUpdate(
            @Param("organizationId") UUID organizationId,
            @Param("roles") Collection<com.example.distrobackend.Domain.enums.UserRole> roles);
    boolean existsByEmail(String email);
    boolean existsByPhoneNumber(String phoneNumber);

    long countByStatus(com.example.distrobackend.Domain.enums.UserStatus status);

    long countByOrganization_Id(UUID organizationId);

    @Query("select u.organization.id, count(u) from User u "
            + "where u.organization.id in :organizationIds group by u.organization.id")
    List<Object[]> countByOrganizationIds(@Param("organizationIds") Collection<UUID> organizationIds);
}
