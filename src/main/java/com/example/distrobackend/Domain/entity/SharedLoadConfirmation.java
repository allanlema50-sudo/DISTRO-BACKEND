package com.example.distrobackend.Domain.entity;

import com.example.distrobackend.Domain.enums.SharedLoadDecision;
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
@Table(name = "shared_load_confirmations", uniqueConstraints =
        @UniqueConstraint(name = "uk_shared_load_confirmation_org", columnNames = {"shared_load_id", "organization_id"}))
@Getter @Setter @NoArgsConstructor
public class SharedLoadConfirmation {
    @Id @GeneratedValue @JdbcTypeCode(SqlTypes.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shared_load_id", nullable = false)
    private SharedLoad sharedLoad;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "confirmed_by", nullable = false)
    private User confirmedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 20)
    private SharedLoadDecision decision;

    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
