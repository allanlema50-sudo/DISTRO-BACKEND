package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.ProviderWebhookEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProviderWebhookProcessingJob {
    private final ProviderWebhookInboxService inbox;
    private final CentralizedCheckoutService checkoutService;
    private final PaystackSettlementService settlementService;
    private final PaymentRefundService refundService;
    private final PaymentDisputeService disputeService;
    private final CustomerOrderPaystackPaymentService customerOrderPaymentService;

    @Value("${app.payments.webhook-processing-batch-size:25}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${PAYMENT_WEBHOOK_POLL_INTERVAL_MS:2000}")
    public void processDueEvents() {
        inbox.recoverStaleProcessing();
        int limit = Math.max(1, Math.min(batchSize, 100));
        for (int i = 0; i < limit; i++) {
            ProviderWebhookInboxService.Claim claim = inbox.claimNext();
            if (claim == null) return;
            UUID eventId = claim.eventId();
            try {
                ProviderWebhookEvent event = inbox.load(eventId);
                if (!"PAYSTACK".equals(event.getProvider())) {
                    throw new IllegalStateException("Unsupported webhook provider");
                }
                PaystackPaymentService.Webhook webhook = new PaystackPaymentService.Webhook(
                        event.getEventName(), event.getProviderReference(), event.getProviderStatus(),
                        event.getAmountMinor(), event.getCurrency(), event.getGatewayResponse(), event.getTransferCode(),
                        event.getProviderDisputeId(), event.getDisputeResolution(), event.getDisputeDueAt(),
                        event.getProviderTransactionReference() == null
                                ? event.getProviderReference() : event.getProviderTransactionReference());
                if (event.getEventName().startsWith("transfer.")) settlementService.processPaystackWebhook(webhook);
                else if (event.getEventName().startsWith("refund.")) refundService.processWebhook(webhook);
                else if (event.getEventName().startsWith("charge.dispute.")) disputeService.processWebhook(webhook);
                else if (!customerOrderPaymentService.processWebhook(webhook)) {
                    checkoutService.processPaystackWebhook(webhook);
                }
                inbox.markProcessed(eventId, claim.claimToken());
            } catch (Exception ex) {
                log.warn("Provider webhook event {} processing attempt failed", eventId, ex);
                inbox.markFailed(eventId, claim.claimToken(), ex);
            }
        }
    }
}
