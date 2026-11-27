package com.example.distrobackend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "paystack.enabled", havingValue = "true")
public class PaystackCustomerOrderReconciliationJob {
    private final CustomerOrderPaystackPaymentService customerOrderPaymentService;

    @Scheduled(fixedDelayString = "${paystack.reconciliation-scan-ms:60000}")
    public void reconcilePendingCustomerPayments() {
        customerOrderPaymentService.reconcileDueAttempts();
    }
}
