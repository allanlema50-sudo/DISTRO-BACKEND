package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.Trip;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TripRepository extends JpaRepository<Trip, UUID> {

    boolean existsByIdAndRider_Id(UUID tripId, UUID riderId);

    boolean existsByIdAndOrder_Customer_Id(UUID tripId, UUID customerId);

    boolean existsByIdAndOrganization_Id(UUID tripId, UUID organizationId);
}
