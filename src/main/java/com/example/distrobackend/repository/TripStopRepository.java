package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.TripStop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface TripStopRepository extends JpaRepository<TripStop, UUID> {
}
