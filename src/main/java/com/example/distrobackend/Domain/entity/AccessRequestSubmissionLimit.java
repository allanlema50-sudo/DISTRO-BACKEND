package com.example.distrobackend.Domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * The single database row used to coordinate public onboarding admission
 * across all application instances.
 */
@Entity
@Table(name = "access_request_submission_limits")
@Getter
@Setter
@NoArgsConstructor
public class AccessRequestSubmissionLimit {
    public static final String GLOBAL_ID = "GLOBAL";

    @Id
    @Column(length = 16)
    private String id;

    @Column(name = "window_started_at", nullable = false)
    private OffsetDateTime windowStartedAt;

    @Column(name = "submission_count", nullable = false)
    private int submissionCount;
}
