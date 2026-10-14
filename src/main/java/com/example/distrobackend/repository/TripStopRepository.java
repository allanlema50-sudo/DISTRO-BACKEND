package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.TripStop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TripStopRepository extends JpaRepository<TripStop, UUID> {
}
