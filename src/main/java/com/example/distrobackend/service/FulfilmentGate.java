package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.PurchaseOrder;
import com.example.distrobackend.Domain.entity.PurchaseOrderSettlement;
import com.example.distrobackend.Domain.enums.SettlementStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;

import java.math.BigDecimal;
import java.util.Collection;

/**
 * The single place that decides whether a direct purchase order may move
 * through warehouse fulfilment (pick, pack, dispatch).
 *
 * <p>Direct purchase orders are pay-now: they need a SETTLED settlement for the exact total.
 *
 * <p>This gate governs fulfilment only. Recording a payment that has actually
 * arrived (a confirmed bank transfer) never calls it, so a later dispute that
 * freezes fulfilment, added here in Phase 3, cannot stop a payment from being
 * recorded. Orders that belong to a marketplace checkout are gated by the
 * checkout payment, not by this class.
 */
public final class FulfilmentGate {

    private FulfilmentGate() { }

    public static boolean paymentSatisfied(PurchaseOrder order, Collection<PurchaseOrderSettlement> settlements) {
        return paidInFull(order, settlements);
    }

    public static void requirePaymentSatisfied(PurchaseOrder order,
                                               Collection<PurchaseOrderSettlement> settlements,
                                               String message) {
        if (!paymentSatisfied(order, settlements)) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, message);
        }
    }

    /** A settlement is SETTLED only when its payments cover the total due exactly. */
    public static boolean paidInFull(PurchaseOrder order, Collection<PurchaseOrderSettlement> settlements) {
        BigDecimal total = order.getTotalAmount();
        return settlements.stream().anyMatch(settlement ->
                settlement.getStatus() == SettlementStatus.SETTLED
                        && settlement.effectiveTotalDue() != null
                        && total != null
                        && settlement.effectiveTotalDue().compareTo(total) == 0
                        && order.getCurrency().equalsIgnoreCase(settlement.getCurrency()));
    }
}
