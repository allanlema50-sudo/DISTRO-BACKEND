package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.Trip;
import com.example.distrobackend.Domain.enums.TripStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {
    
    Page<Trip> findByRiderId(UUID riderId, Pageable pageable);
    
    List<Trip> findByRiderIdAndStatusIn(UUID riderId, List<TripStatus> statuses);
    
    @Query("SELECT DISTINCT t FROM Trip t JOIN t.stops s WHERE s.order.organization.id = :organizationId")
    Page<Trip> findByOrganizationId(@Param("organizationId") UUID organizationId, Pageable pageable);
}
