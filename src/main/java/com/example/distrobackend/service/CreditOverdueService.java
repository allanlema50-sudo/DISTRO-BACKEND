package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.PurchaseOrder;
import com.example.distrobackend.Domain.enums.NotificationPriority;
import com.example.distrobackend.Domain.enums.NotificationType;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.repository.PurchaseOrderRepository;
import com.example.distrobackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Stamps credit orders that have passed their due date with an unpaid balance
 * and tells both parties. Whether an order blocks new credit is always decided
 * from its due date and balance at the moment of the check, so a late or missed
 * run of this job never lets an overdue distributor order more on credit.
 */
@Service
@RequiredArgsConstructor
public class CreditOverdueService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public int flagOverdue(OffsetDateTime now, int batchSize) {
        int flagged = 0;
        for (UUID id : purchaseOrderRepository.findCreditOrdersToFlagOverdue(now, batchSize)) {
            PurchaseOrder order = purchaseOrderRepository.findByIdForPayment(id).orElse(null);
            if (order == null || order.getOverdueAt() != null) {
                continue;
            }
            order.setOverdueAt(now);
            purchaseOrderRepository.save(order);
            String number = order.getPurchaseOrderNumber();
            userRepository.findByOrganization_IdAndRole(order.getBuyerOrganization().getId(),
                            UserRole.DISTRIBUTOR_ADMIN, Pageable.unpaged())
                    .forEach(user -> notificationService.create(user, "Credit payment overdue",
                            "Purchase order " + number + " is past its due date. New credit orders are blocked "
                                    + "until it is paid.", NotificationType.ORDER, NotificationPriority.HIGH,
                            order.getId(), "PURCHASE_ORDER"));
            userRepository.findByOrganization_IdAndRole(order.getSupplierOrganization().getId(),
                            UserRole.MANUFACTURER_ADMIN, Pageable.unpaged())
                    .forEach(user -> notificationService.create(user, "Credit payment overdue",
                            "Purchase order " + number + " from " + order.getBuyerOrganization().getName()
                                    + " is overdue.", NotificationType.ORDER, NotificationPriority.MEDIUM,
                            order.getId(), "PURCHASE_ORDER"));
            flagged++;
        }
        return flagged;
    }
}
