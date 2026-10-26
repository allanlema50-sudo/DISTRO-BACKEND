package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.AccessRequestSubmissionLimit;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.repository.AccessRequestSubmissionLimitRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Coordinates a bounded public onboarding admission window in the database.
 * The row lock makes the limit effective across multiple API instances.
 */
@Service
@RequiredArgsConstructor
public class AccessRequestSubmissionLimiter {
    private final AccessRequestSubmissionLimitRepository repository;

    @Value("${app.onboarding.submission-window:15m}")
    private Duration submissionWindow;

    @Value("${app.onboarding.max-submissions-per-window:100}")
    private int maxSubmissionsPerWindow;

    @PostConstruct
    void validateConfiguration() {
        if (submissionWindow.isZero() || submissionWindow.isNegative()) {
            throw new IllegalStateException("app.onboarding.submission-window must be positive");
        }
        if (maxSubmissionsPerWindow <= 0) {
            throw new IllegalStateException("app.onboarding.max-submissions-per-window must be positive");
        }
    }

    @Transactional
    public void acquire() {
        AccessRequestSubmissionLimit limit = repository
                .findByIdForUpdate(AccessRequestSubmissionLimit.GLOBAL_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "Onboarding submission limit row is missing; run the latest database migration"));

        OffsetDateTime now = OffsetDateTime.now();
        if (!limit.getWindowStartedAt().plus(submissionWindow).isAfter(now)) {
            limit.setWindowStartedAt(now);
            limit.setSubmissionCount(0);
        }
        if (limit.getSubmissionCount() >= maxSubmissionsPerWindow) {
            throw new ApiException(ErrorCode.ONBOARDING_RATE_LIMITED);
        }
        limit.setSubmissionCount(limit.getSubmissionCount() + 1);
    }
}
