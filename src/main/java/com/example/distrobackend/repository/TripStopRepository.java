package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.TripStop;
import com.example.distrobackend.Domain.enums.StopStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TripStopRepository extends JpaRepository<TripStop, UUID> {
    Optional<TripStop> findFirstByOrder_IdAndStatusOrderBySequenceAsc(UUID orderId, StopStatus status);

    @Query("select s from TripStop s join fetch s.trip where s.order.id = :orderId")
    List<TripStop> findByOrderIdWithTrip(@Param("orderId") UUID orderId);
}
