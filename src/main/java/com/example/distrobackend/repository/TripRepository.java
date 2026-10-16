package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.Trip;
import com.example.distrobackend.Domain.enums.TripStatus;
import com.example.distrobackend.Domain.enums.TripType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {

    @Query(
        value = """
            SELECT t
            FROM Trip t
            LEFT JOIN FETCH t.order o
            LEFT JOIN FETCH o.customer c
            LEFT JOIN FETCH t.rider r
            WHERE t.tripType = :tripType
            AND (
                :search = ''
                OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(r.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
            )
            """,
        countQuery = """
            SELECT COUNT(t)
            FROM Trip t
            LEFT JOIN t.order o
            LEFT JOIN o.customer c
            LEFT JOIN t.rider r
            WHERE t.tripType = :tripType
            AND (
                :search = ''
                OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(r.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
            )
            """
    )
    Page<Trip> searchDeliveryTrips(
            @Param("tripType") TripType tripType,
            @Param("search") String search,
            Pageable pageable
    );

    @Query(
        value = """
            SELECT t
            FROM Trip t
            LEFT JOIN FETCH t.order o
            LEFT JOIN FETCH o.customer c
            LEFT JOIN FETCH t.rider r
            WHERE t.tripType = :tripType
            AND o.organization.id = :organizationId
            AND (
                :search = ''
                OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(r.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
            )
            """,
        countQuery = """
            SELECT COUNT(t)
            FROM Trip t
            LEFT JOIN t.order o
            LEFT JOIN o.customer c
            LEFT JOIN t.rider r
            WHERE t.tripType = :tripType
            AND o.organization.id = :organizationId
            AND (
                :search = ''
                OR LOWER(o.orderNumber) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(c.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(c.email) LIKE LOWER(CONCAT('%', :search, '%'))
                OR LOWER(r.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
            )
            """
    )
    Page<Trip> searchDeliveryTripsByOrganizationId(
            @Param("tripType") TripType tripType,
            @Param("organizationId") UUID organizationId,
            @Param("search") String search,
            Pageable pageable
    );

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
            @Param("statuses") Collection<TripStatus> statuses
    );

    Optional<Trip> findFirstByOrderIdOrderByCreatedAtDesc(UUID orderId);

    long countByTripType(TripType tripType);

long countByTripTypeAndStatus(
        TripType tripType,
        TripStatus status
);
}