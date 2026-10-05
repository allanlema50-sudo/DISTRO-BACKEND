package com.example.distrobackend.Domain.entity;

import com.example.distrobackend.Domain.enums.AccessRequestStatus;
import com.example.distrobackend.Domain.enums.Organizationtype;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "access_requests")
@Getter @Setter @NoArgsConstructor
public class AccessRequest {
    @Id @GeneratedValue @JdbcTypeCode(SqlTypes.UUID)
    private UUID id;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;
    @Column(name = "phone_number", nullable = false, unique = true, length = 20)
    private String phoneNumber;
    @Column(name = "personal_email", nullable = false, unique = true, length = 150)
    private String personalEmail;
    @Column(name = "organization_email", nullable = false, length = 150)
    private String organizationEmail;
    @Column(name = "organization_name", nullable = false, length = 150)
    private String organizationName;
    @Enumerated(EnumType.STRING)
    @Column(name = "organization_type", nullable = false)
    private Organizationtype organizationType;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccessRequestStatus status = AccessRequestStatus.PENDING;
    @Column(name = "activation_token_hash", length = 64)
    private String activationTokenHash;
    @Column(name = "activation_expires_at")
    private OffsetDateTime activationExpiresAt;
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
