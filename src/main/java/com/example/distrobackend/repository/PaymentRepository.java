package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.Payment;
import com.example.distrobackend.Domain.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    @Query("""
        SELECT COUNT(p)
        FROM Payment p
        WHERE p.order.organization.id = :organizationId
        """)
    long countByOrganizationId(
            @Param("organizationId") UUID organizationId
    );

    @Query("""
        SELECT COUNT(p)
        FROM Payment p
        WHERE p.order.organization.id = :organizationId
        AND p.status = :status
        """)
    long countByOrganizationIdAndStatus(
            @Param("organizationId") UUID organizationId,
            @Param("status") PaymentStatus status
    );
}
