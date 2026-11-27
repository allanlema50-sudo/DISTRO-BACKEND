package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.PlatformFeeSetting;
import com.example.distrobackend.Domain.entity.PurchaseOrder;
import com.example.distrobackend.Domain.entity.PurchaseOrderSettlement;
import com.example.distrobackend.Domain.enums.FeePayer;
import com.example.distrobackend.Domain.enums.PaymentTerms;
import com.example.distrobackend.Domain.enums.PlatformFeeType;
import com.example.distrobackend.Domain.enums.SettlementStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FulfilmentGateTest {

    private static PurchaseOrder order(PaymentTerms terms, String total) {
        PurchaseOrder order = new PurchaseOrder();
        order.setPaymentTerms(terms);
        order.setTotalAmount(new BigDecimal(total));
        return order;
    }

    private static PurchaseOrderSettlement settlement(SettlementStatus status, String amount, String paid) {
        PurchaseOrderSettlement settlement = new PurchaseOrderSettlement();
        settlement.setStatus(status);
        settlement.setAmount(new BigDecimal(amount));
        settlement.setTotalDue(new BigDecimal(amount));
        settlement.setAmountPaid(new BigDecimal(paid));
        return settlement;
    }

    @Test
    void payNowNeedsASettledSettlementForTheExactTotal() {
        PurchaseOrder order = order(PaymentTerms.PAY_NOW, "500.00");
        assertFalse(FulfilmentGate.paymentSatisfied(order, List.of()));
        assertFalse(FulfilmentGate.paymentSatisfied(order, List.of(settlement(SettlementStatus.PENDING, "500.00", "0.00"))));
        assertFalse(FulfilmentGate.paymentSatisfied(order, List.of(settlement(SettlementStatus.SETTLED, "400.00", "400.00"))));
        assertTrue(FulfilmentGate.paymentSatisfied(order, List.of(settlement(SettlementStatus.SETTLED, "500.00", "500.00"))));
        ApiException ex = assertThrows(ApiException.class,
                () -> FulfilmentGate.requirePaymentSatisfied(order, List.of(), "unpaid"));
        assertEquals(ErrorCode.PAYMENT_STATE_CONFLICT, ex.getCode());
    }

    @Test
    void creditOrdersMayBeFulfilledBeforePayment() {
        PurchaseOrder order = order(PaymentTerms.CREDIT, "500.00");
        assertTrue(FulfilmentGate.paymentSatisfied(order, List.of()));
        assertFalse(FulfilmentGate.paidInFull(order, List.of()));
    }

    @Test
    void feeIsZeroWithoutASettingOrWithAZeroValue() {
        assertEquals(0, BigDecimal.ZERO.compareTo(PlatformFeeService.computeFee(null, new BigDecimal("500.00"), "KES")));
        assertEquals(0, BigDecimal.ZERO.compareTo(PlatformFeeService.computeFee(
                setting(PlatformFeeType.PERCENT, "0.00"), new BigDecimal("500.00"), "KES")));
    }

    @Test
    void percentFeeRoundsHalfUpAndFlatFeeNeverExceedsTheTotal() {
        assertEquals(new BigDecimal("12.50"), PlatformFeeService.computeFee(
                setting(PlatformFeeType.PERCENT, "2.50"), new BigDecimal("500.00"), "KES"));
        assertEquals(new BigDecimal("0.04"), PlatformFeeService.computeFee(
                setting(PlatformFeeType.PERCENT, "0.35"), new BigDecimal("10.00"), "KES"));   // 0.035 rounds up
        assertEquals(new BigDecimal("50.00"), PlatformFeeService.computeFee(
                setting(PlatformFeeType.FLAT, "50.00"), new BigDecimal("500.00"), "KES"));
        assertEquals(new BigDecimal("20.00"), PlatformFeeService.computeFee(
                setting(PlatformFeeType.FLAT, "50.00"), new BigDecimal("20.00"), "KES"));
        assertEquals(0, BigDecimal.ZERO.compareTo(PlatformFeeService.computeFee(
                setting(PlatformFeeType.FLAT, "50.00"), new BigDecimal("500.00"), "USD")));   // flat is currency-bound
    }

    @Test
    void bankReferencesAreNormalisedAndValidated() {
        assertEquals("FT 123/45", BankTransferSettlementService.normalizeReference("  ft   123/45 "));
        for (String bad : new String[] {null, "", "ab", "<script>", "ref;drop table", "x".repeat(101)}) {
            ApiException ex = assertThrows(ApiException.class, () -> BankTransferSettlementService.normalizeReference(bad));
            assertEquals(ErrorCode.BAD_REQUEST, ex.getCode());
        }
    }

    private static PlatformFeeSetting setting(PlatformFeeType type, String value) {
        PlatformFeeSetting setting = new PlatformFeeSetting();
        setting.setFeeType(type);
        setting.setFeeValue(new BigDecimal(value));
        setting.setCurrency("KES");
        setting.setFeePayer(FeePayer.SUPPLIER);
        return setting;
    }
}
