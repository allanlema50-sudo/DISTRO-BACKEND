package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.TripOperationalAction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TripOperationalActionRepository extends JpaRepository<TripOperationalAction, UUID> {
    Page<TripOperationalAction> findByTrip_IdOrderByCreatedAtDesc(UUID tripId, Pageable pageable);
}
