package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    @Query(value = "select a from AuditLog a left join fetch a.actor actor "
            + "where (:actorId is null or actor.id = :actorId) "
            + "and (:entityType is null or a.entityType = :entityType) "
            + "and (:entityId is null or a.entityId = :entityId) "
            + "order by a.createdAt desc",
            countQuery = "select count(a) from AuditLog a left join a.actor actor "
                    + "where (:actorId is null or actor.id = :actorId) "
                    + "and (:entityType is null or a.entityType = :entityType) "
                    + "and (:entityId is null or a.entityId = :entityId)")
    Page<AuditLog> search(@Param("actorId") UUID actorId,
                          @Param("entityType") String entityType,
                          @Param("entityId") UUID entityId,
                          Pageable pageable);

    @Query(value = "select a from AuditLog a left join fetch a.actor actor "
            + "where actor.organization.id = :organizationId order by a.createdAt desc",
            countQuery = "select count(a) from AuditLog a join a.actor actor "
                    + "where actor.organization.id = :organizationId")
    Page<AuditLog> findByOrganizationId(@Param("organizationId") UUID organizationId, Pageable pageable);

    @Query("select a from AuditLog a left join fetch a.actor where a.id = :id")
    java.util.Optional<AuditLog> findByIdWithActor(@Param("id") Long id);
}
