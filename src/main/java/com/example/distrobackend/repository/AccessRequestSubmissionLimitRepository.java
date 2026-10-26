package com.example.distrobackend.repository;

import com.example.distrobackend.Domain.entity.AccessRequestSubmissionLimit;
import jakarta.persistence.LockModeType;
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
}
