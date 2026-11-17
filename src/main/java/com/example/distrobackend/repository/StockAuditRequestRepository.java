package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.StockAuditRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface StockAuditRequestRepository extends JpaRepository<StockAuditRequest, UUID> {}
