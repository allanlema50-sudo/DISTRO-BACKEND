package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.TripStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface TripStatusHistoryRepository extends JpaRepository<TripStatusHistory, UUID> {
}
