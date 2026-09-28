package com.example.distrobackend.Domain.entity;

import com.example.distrobackend.Domain.enums.OtpPurpose;
import com.example.distrobackend.Domain.enums.OtpStatus;
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
@Table(name = "otp_verifications")
@Getter
@Setter
@NoArgsConstructor

public class OtpVerification {
@Id
@GeneratedValue
@JdbcTypeCode(SqlTypes.UUID)
private UUID id;

    // nullable: OTP flows (e.g. registration) can precede a user record
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private JsonType.User user;

    @Column(name = "destination", nullable = false, length = 150)
    private String destination;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false)
    private OtpPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OtpStatus status = OtpStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private short attemptCount = 0;

    @Column(name = "max_attempts", nullable = false)
    private short maxAttempts = 5;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "verified_at")
    private OffsetDateTime verifiedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
