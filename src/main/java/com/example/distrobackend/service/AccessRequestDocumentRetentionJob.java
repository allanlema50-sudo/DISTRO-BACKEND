package com.example.distrobackend.service;

import com.example.distrobackend.repository.AccessRequestDocumentRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

/** Removes old private evidence while retaining request metadata for audit. */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccessRequestDocumentRetentionJob {
    private final AccessRequestDocumentRepository documentRepository;

    @Value("${app.onboarding.document-retention:30d}")
    private Duration documentRetention;

    @PostConstruct
    void validateConfiguration() {
        if (documentRetention.isZero() || documentRetention.isNegative()) {
            throw new IllegalStateException("app.onboarding.document-retention must be positive");
        }
    }

    @Scheduled(fixedDelayString = "${ONBOARDING_DOCUMENT_RETENTION_SCAN_MS:86400000}",
            initialDelayString = "${ONBOARDING_DOCUMENT_RETENTION_INITIAL_DELAY_MS:60000}")
    @Transactional
    public void purgeRetainedEvidence() {
        OffsetDateTime cutoff = OffsetDateTime.now().minus(documentRetention);
        int deleted = documentRepository.deleteRejectedOrExpiredDocumentsBefore(cutoff);
        if (deleted > 0) {
            log.info("Purged {} onboarding document(s) retained before {}", deleted, cutoff);
        }
    }
}
