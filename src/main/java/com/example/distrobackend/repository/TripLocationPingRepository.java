package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.TripLocationPing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TripLocationPingRepository extends JpaRepository<TripLocationPing, Long> {
    List<TripLocationPing> findByTripIdOrderByRecordedAtDesc(UUID tripId);
}
