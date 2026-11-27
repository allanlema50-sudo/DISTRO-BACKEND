package com.example.distrobackend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/** Hourly by default; set CREDIT_OVERDUE_CRON to change it. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CreditOverdueJob {

    private static final int BATCH_SIZE = 200;

    private final CreditOverdueService overdueService;

    @Scheduled(cron = "${credit.overdue-cron:0 5 * * * *}", zone = "Africa/Nairobi")
    public void run() {
        try {
            int flagged;
            do {
                flagged = overdueService.flagOverdue(OffsetDateTime.now(), BATCH_SIZE);
                if (flagged > 0) {
                    log.info("Flagged {} overdue credit purchase orders", flagged);
                }
            } while (flagged == BATCH_SIZE);
        } catch (RuntimeException ex) {
            log.error("Credit overdue scan failed", ex);
        }
    }
}
