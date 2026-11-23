package com.example.distrobackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;

/** Acquires transaction-scoped PostgreSQL advisory locks for finance invariants. */
@Service
@RequiredArgsConstructor
public class PostgresTransactionLockService {
    private final JdbcTemplate jdbcTemplate;

    @Transactional(propagation = Propagation.MANDATORY)
    public void lock(String key) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {
                statement.setString(1, key);
                statement.execute();
            }
            return null;
        });
    }
}
