
package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Modifying
    @Query("UPDATE AuditLog a SET a.actor = null WHERE a.actor.id = :userId")
    int clearActorForUser(@Param("userId") UUID userId);

    @EntityGraph(attributePaths = {"actor"})
    @Query("""
        SELECT a FROM AuditLog a
        LEFT JOIN a.actor u
        WHERE
            :search = ''
            OR LOWER(COALESCE(a.action, '')) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(COALESCE(a.entityType, '')) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(CAST(a.entityId AS string)) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(COALESCE(u.fullName, '')) LIKE LOWER(CONCAT('%', :search, '%'))
            OR LOWER(COALESCE(u.email, '')) LIKE LOWER(CONCAT('%', :search, '%'))
            OR CAST(a.ipAddress AS string) LIKE CONCAT('%', :search, '%')
        ORDER BY a.createdAt DESC
        """)
    Page<AuditLog> searchAuditLogs(
        @Param("search") String search,
        Pageable pageable
    );
}
