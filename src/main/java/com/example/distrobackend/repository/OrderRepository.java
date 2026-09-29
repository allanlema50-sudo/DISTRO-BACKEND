package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.Order;
import com.example.distrobackend.Domain.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    long countByStatus(OrderStatus status);
}