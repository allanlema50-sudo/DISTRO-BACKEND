package com.example.distrobackend.Domain.entity;

import com.example.distrobackend.Domain.enums.UserRole;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;
import org.hibernate.usertype.UserType;

import java.time.OffsetDateTime;
import java.util.UUID;

public class JsonType implements UserType {
    @Entity
    @Table(name = "users")
    @Getter
    @Setter
    @NoArgsConstructor
    public static class User {

        @Id
        @GeneratedValue
        @JdbcTypeCode(SqlTypes.UUID)
        private UUID id;

        @Column(name = "full_name", nullable = false, length = 150)
        private String fullName;

        @Column(name = "email", unique = true, length = 150)
        private String email;

        @Column(name = "phone_number", nullable = false, unique = true, length = 20)
        private String phoneNumber;

        @Column(name = "password_hash", nullable = false)
        private String passwordHash;

        @Enumerated(EnumType.STRING)
        @Column(name = "role", nullable = false)
        private UserRole role = UserRole.CUSTOMER;

        @Enumerated(EnumType.STRING)
        @Column(name = "status", nullable = false)
        private UserStatus status = UserStatus.ACTIVE;

        @Column(name = "is_phone_verified", nullable = false)
        private boolean phoneVerified = false;

        @Column(name = "is_email_verified", nullable = false)
        private boolean emailVerified = false;

        @CreationTimestamp
        @Column(name = "created_at", nullable = false, updatable = false)
        private OffsetDateTime createdAt;

        @UpdateTimestamp
        @Column(name = "updated_at", nullable = false)
        private OffsetDateTime updatedAt;
    }
}
