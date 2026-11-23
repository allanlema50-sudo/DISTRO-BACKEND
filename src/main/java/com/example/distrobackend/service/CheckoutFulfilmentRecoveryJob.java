package com.example.distrobackend.service;

import com.example.distrobackend.repository.PurchaseOrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class CheckoutFulfilmentRecoveryJob {
    private static final int MAX_BATCH_SIZE = 100;

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderShipmentService shipmentService;

    @Scheduled(fixedDelayString = "${purchase-order.fulfilment-recovery-scan-ms:60000}")
    public void retryPaidCheckoutsWithoutShipmentPreparation() {
        var paymentIds = purchaseOrderRepository.findCheckoutPaymentsNeedingFulfilmentRecovery(
                OffsetDateTime.now(), PageRequest.of(0, MAX_BATCH_SIZE));
        for (var paymentId : paymentIds) {
            try {
                shipmentService.retryPaidCheckoutFulfilment(paymentId);
            } catch (RuntimeException ex) {
                log.error("Automatic shipment preparation failed for paid checkout payment {}", paymentId, ex);
                try {
                    shipmentService.recordCheckoutFulfilmentRecoveryFailure(paymentId);
                } catch (RuntimeException recordFailure) {
                    log.error("Could not persist recovery state for paid checkout payment {}", paymentId,
                            recordFailure);
                }
            }
        }
    }
}
