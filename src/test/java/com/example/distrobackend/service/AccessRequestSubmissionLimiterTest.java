package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.AccessRequestSubmissionLimit;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.repository.AccessRequestSubmissionLimitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessRequestSubmissionLimiterTest {
    @Mock
    private AccessRequestSubmissionLimitRepository repository;

    private AccessRequestSubmissionLimiter limiter;

    @BeforeEach
    void setUp() {
        limiter = new AccessRequestSubmissionLimiter(repository);
        ReflectionTestUtils.setField(limiter, "submissionWindow", Duration.ofMinutes(15));
        ReflectionTestUtils.setField(limiter, "maxSubmissionsPerWindow", 2);
    }

    @Test
    void incrementsTheSharedWindowCounter() {
        AccessRequestSubmissionLimit row = row(0, OffsetDateTime.now());
        when(repository.findByIdForUpdate(AccessRequestSubmissionLimit.GLOBAL_ID))
                .thenReturn(Optional.of(row));

        limiter.acquire();

        assertThat(row.getSubmissionCount()).isEqualTo(1);
        verify(repository).findByIdForUpdate(AccessRequestSubmissionLimit.GLOBAL_ID);
    }

    @Test
    void rejectsWhenTheSharedWindowIsFull() {
        AccessRequestSubmissionLimit row = row(2, OffsetDateTime.now());
        when(repository.findByIdForUpdate(AccessRequestSubmissionLimit.GLOBAL_ID))
                .thenReturn(Optional.of(row));

        assertThatThrownBy(limiter::acquire)
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Too many onboarding submissions");
    }

    @Test
    void resetsAnExpiredWindowBeforeCountingTheSubmission() {
        AccessRequestSubmissionLimit row = row(2, OffsetDateTime.now().minusHours(1));
        when(repository.findByIdForUpdate(AccessRequestSubmissionLimit.GLOBAL_ID))
                .thenReturn(Optional.of(row));

        limiter.acquire();

        assertThat(row.getSubmissionCount()).isEqualTo(1);
    }

    @Test
    void initializesTheGlobalRowWhenTheSchemaExistsWithoutItsSeedData() {
        AccessRequestSubmissionLimit row = row(0, OffsetDateTime.now());
        when(repository.findByIdForUpdate(AccessRequestSubmissionLimit.GLOBAL_ID))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(row));

        limiter.acquire();

        verify(repository).ensureGlobalRow();
        assertThat(row.getSubmissionCount()).isEqualTo(1);
    }

    private AccessRequestSubmissionLimit row(int count, OffsetDateTime startedAt) {
        AccessRequestSubmissionLimit row = new AccessRequestSubmissionLimit();
        row.setId(AccessRequestSubmissionLimit.GLOBAL_ID);
        row.setSubmissionCount(count);
        row.setWindowStartedAt(startedAt);
        return row;
    }
}
