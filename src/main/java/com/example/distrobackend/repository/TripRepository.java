package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.Trip;
import com.example.distrobackend.Domain.enums.TripStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TripRepository extends JpaRepository<Trip, UUID> {
    boolean existsByIdAndRider_Id(UUID tripId, UUID riderId);

    @Query("""
            select case when count(t) > 0 then true else false end
            from Trip t
            left join t.stops s
            left join t.order tripOrder
            left join s.order stopOrder
            where t.id = :tripId
              and (tripOrder.customer.id = :customerId or stopOrder.customer.id = :customerId)
            """)
    boolean existsByIdAndCustomerAccess(
            @Param("tripId") UUID tripId, @Param("customerId") UUID customerId);

    boolean existsByIdAndOrganization_Id(UUID tripId, UUID organizationId);

    Page<Trip> findByRiderId(UUID riderId, Pageable pageable);

    Page<Trip> findByOrganization_Id(UUID organizationId, Pageable pageable);

    long countByOrganization_Id(UUID organizationId);

    @Query("select t.organization.id, count(t) from Trip t "
            + "where t.organization.id in :organizationIds group by t.organization.id")
    List<Object[]> countByOrganizationIds(@Param("organizationIds") List<UUID> organizationIds);

    long countByStatus(TripStatus status);

    @Query(value = "select distinct t from Trip t left join t.stops s left join s.order stopOrder "
            + "left join t.order tripOrder "
            + "where stopOrder.customer.id = :customerId or tripOrder.customer.id = :customerId",
            countQuery = "select count(distinct t) from Trip t left join t.stops s left join s.order stopOrder "
                    + "left join t.order tripOrder "
                    + "where stopOrder.customer.id = :customerId or tripOrder.customer.id = :customerId")
    Page<Trip> findByCustomerId(@Param("customerId") UUID customerId, Pageable pageable);

    List<Trip> findByRiderIdAndStatusIn(UUID riderId, List<TripStatus> statuses);

    boolean existsByRider_IdAndStatusInAndIdNot(
            UUID riderId, List<TripStatus> statuses, UUID tripId);

    boolean existsByRider_IdAndStatusIn(UUID riderId, List<TripStatus> statuses);

    boolean existsByVehicle_IdAndStatusInAndIdNot(
            UUID vehicleId, List<TripStatus> statuses, UUID tripId);

    boolean existsByVehicle_IdAndStatusIn(UUID vehicleId, List<TripStatus> statuses);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Trip t join fetch t.organization "
            + "where t.id = :tripId and t.organization.id = :organizationId")
    Optional<Trip> findByIdAndOrganizationForUpdate(
            @Param("tripId") UUID tripId,
            @Param("organizationId") UUID organizationId);

    @Query("SELECT DISTINCT t FROM Trip t JOIN t.stops s WHERE s.order.organization.id = :organizationId")
    Page<Trip> findByOrganizationId(@Param("organizationId") UUID organizationId, Pageable pageable);
}
