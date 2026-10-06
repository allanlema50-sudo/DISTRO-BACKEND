package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.Trip;
import com.example.distrobackend.Domain.enums.TripStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    @Query("""
        SELECT COUNT(t)
        FROM Trip t
        WHERE
            t.order.organization.id = :organizationId
            OR t.stockItem.organizationId = :organizationId
        """)
    long countByOrganizationId(
            @Param("organizationId") UUID organizationId
    );

    @Query("""
        SELECT COUNT(t)
        FROM Trip t
        WHERE
            (
                t.order.organization.id = :organizationId
                OR t.stockItem.organizationId = :organizationId
            )
            AND t.status IN :statuses
        """)
    long countActiveByOrganizationId(
            @Param("organizationId") UUID organizationId,
            @Param("statuses") java.util.Collection<TripStatus> statuses
    );
}
