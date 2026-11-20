package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.TripStop;
import com.example.distrobackend.Domain.enums.StopStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TripStopRepository extends JpaRepository<TripStop, UUID> {
    Optional<TripStop> findFirstByOrder_IdAndStatusOrderBySequenceAsc(UUID orderId, StopStatus status);
}
