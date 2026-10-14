package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.TripLocationPing;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TripLocationPingRepository extends JpaRepository<TripLocationPing, Long> {
    List<TripLocationPing> findByTripIdOrderByRecordedAtDesc(UUID tripId);
}
