package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.AccessRequestSubmissionLimit;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AccessRequestSubmissionLimitRepository
        extends JpaRepository<AccessRequestSubmissionLimit, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select limitRow from AccessRequestSubmissionLimit limitRow where limitRow.id = :id")
    Optional<AccessRequestSubmissionLimit> findByIdForUpdate(@Param("id") String id);

    /**
     * Repairs a missing seed row without creating duplicates when multiple API
     * instances receive the first submissions at the same time.
     */
    @Modifying
    @Query(value = """
            INSERT INTO access_request_submission_limits
                (id, window_started_at, submission_count)
            VALUES ('GLOBAL', CURRENT_TIMESTAMP, 0)
            ON CONFLICT (id) DO NOTHING
            """, nativeQuery = true)
    int ensureGlobalRow();
}
