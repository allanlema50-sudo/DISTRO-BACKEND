package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.CheckoutPayment;
import com.example.distrobackend.Domain.entity.PaymentAllocation;
import com.example.distrobackend.Domain.entity.PaymentRefund;
import com.example.distrobackend.Domain.entity.SupplierSettlement;
import com.example.distrobackend.Domain.enums.CheckoutPaymentStatus;
import com.example.distrobackend.Domain.enums.PaymentProvider;
import com.example.distrobackend.Domain.enums.RefundStatus;
import com.example.distrobackend.Domain.enums.SupplierSettlementStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.CreatePaymentRefundRequest;
import com.example.distrobackend.dto.PaymentRefundResponse;
import com.example.distrobackend.repository.CheckoutPaymentRepository;
import com.example.distrobackend.repository.PaymentAllocationRepository;
import com.example.distrobackend.repository.PaymentRefundRepository;
import com.example.distrobackend.repository.SupplierSettlementRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/** Commits a stable refund intent before any request is sent to Paystack. */
@Service
@RequiredArgsConstructor
public class PaymentRefundIntentService {
    private final CheckoutPaymentRepository paymentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final SupplierSettlementRepository settlementRepository;
    private final PaymentRefundRepository refundRepository;
    private final UserRepository userRepository;
    private final CentralizedCheckoutService checkoutService;

    @Transactional
    public PreparedRefund prepare(AuthenticatedUser actor, UUID paymentId, CreatePaymentRefundRequest request) {
        CheckoutPayment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout payment not found"));
        PaymentRefund existing = refundRepository.findTopByPayment_IdOrderByCreatedAtDescIdDesc(paymentId)
                .orElse(null);
        if (existing != null && existing.getStatus() != RefundStatus.FAILED) {
            return new PreparedRefund(PaymentRefundResponse.from(existing), null, false);
        }
        if (request == null || request.reason() == null || request.reason().isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Refund reason is required");
        }
        if (payment.getProvider() != PaymentProvider.PAYSTACK
                || payment.getStatus() != CheckoutPaymentStatus.SUCCESS || payment.isDemo()) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Only a successful, non-demo Paystack payment can be refunded");
        }
        List<PaymentAllocation> allocations = allocationRepository
                .findByPayment_IdOrderByCreatedAtAsc(paymentId);
        if (allocations.isEmpty()) {
            throw new ApiException(ErrorCode.CONFLICT, "Payment has no allocations to reverse");
        }
        List<SupplierSettlement> settlements = settlementRepository.findByPaymentForUpdate(paymentId);
        if (settlements.size() != allocations.size() || settlements.stream().anyMatch(settlement ->
                settlement.getStatus() != SupplierSettlementStatus.PENDING || settlement.getSettlementBatch() != null)) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Refund is available only before any supplier settlement has been batched or paid");
        }

        checkoutService.prepareRefund(payment);
        PaymentRefund refund = new PaymentRefund();
        refund.setPayment(payment);
        refund.setRequestedBy(userRepository.getReferenceById(actor.userId()));
        refund.setProviderReference("lf_refund_" + UUID.randomUUID().toString().replace("-", ""));
        refund.setAmount(payment.getAmount());
        refund.setCurrency(payment.getCurrency());
        refund.setReason(request.reason().trim());
        refund.setStatus(RefundStatus.REQUESTED);
        refund.setNextReconciliationAt(OffsetDateTime.now().plusMinutes(1));
        refund = refundRepository.saveAndFlush(refund);

        Dispatch dispatch = new Dispatch(refund.getId(), refund.getProviderReference(),
                payment.getProviderReference(), refund.getAmount(), refund.getCurrency(), refund.getReason(),
                "LogiFlow full checkout refund " + payment.getCheckout().getCheckoutReference());
        return new PreparedRefund(PaymentRefundResponse.from(refund), dispatch, true);
    }

    public record PreparedRefund(PaymentRefundResponse response, Dispatch dispatch, boolean shouldDispatch) {}
    public record Dispatch(UUID refundId, String providerReference, String transactionReference,
                           java.math.BigDecimal amount, String currency, String customerNote,
                           String merchantNote) {}
}
