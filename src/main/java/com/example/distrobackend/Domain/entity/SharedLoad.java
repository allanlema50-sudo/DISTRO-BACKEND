package com.example.distrobackend.Domain.entity;

import com.example.distrobackend.Domain.enums.SharedLoadStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "shared_loads", indexes = {
        @Index(name = "idx_shared_loads_proposer_status", columnList = "proposer_organization_id,status"),
        @Index(name = "idx_shared_loads_candidate_status", columnList = "candidate_organization_id,status")
})
@Getter @Setter @NoArgsConstructor
public class SharedLoad {
    @Id @GeneratedValue @JdbcTypeCode(SqlTypes.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposed_trip_id", nullable = false)
    private Trip proposedTrip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_trip_id", nullable = false)
    private Trip candidateTrip;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposer_organization_id", nullable = false)
    private Organization proposerOrganization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_organization_id", nullable = false)
    private Organization candidateOrganization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proposed_by", nullable = false)
    private User proposedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SharedLoadStatus status = SharedLoadStatus.RECOMMENDED;

    @Column(name = "proposal_note", columnDefinition = "TEXT")
    private String proposalNote;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    @OneToMany(mappedBy = "sharedLoad", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<SharedLoadConfirmation> confirmations = new ArrayList<>();

    @CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
    @UpdateTimestamp @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
    @Version @Column(name = "version", nullable = false)
    private long version;

    public void addConfirmation(SharedLoadConfirmation confirmation) {
        confirmations.add(confirmation);
        confirmation.setSharedLoad(this);
    }
}
