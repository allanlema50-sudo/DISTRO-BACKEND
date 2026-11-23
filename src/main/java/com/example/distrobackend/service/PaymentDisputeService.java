package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.CheckoutPayment;
import com.example.distrobackend.Domain.entity.PaymentDispute;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.NotificationPriority;
import com.example.distrobackend.Domain.enums.NotificationType;
import com.example.distrobackend.Domain.enums.PaymentDisputeStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.PaymentDisputeResponse;
import com.example.distrobackend.dto.UpdatePaymentDisputeReviewRequest;
import com.example.distrobackend.repository.CheckoutPaymentRepository;
import com.example.distrobackend.repository.PaymentDisputeRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PaymentDisputeService {
    private final PaymentDisputeRepository disputeRepository;
    private final CheckoutPaymentRepository paymentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public void processWebhook(PaystackPaymentService.Webhook webhook) {
        if (!Set.of("charge.dispute.create", "charge.dispute.remind", "charge.dispute.resolve")
                .contains(webhook.event())) return;
        String disputeId = webhook.disputeId();
        if (disputeId == null || disputeId.isBlank() || disputeId.length() > 80) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Paystack dispute event is missing its dispute ID");
        }
        PaymentDispute dispute = disputeRepository.findByProviderDisputeIdForUpdate(disputeId)
                .orElseGet(PaymentDispute::new);
        boolean created = dispute.getId() == null;
        if (created) dispute.setProviderDisputeId(disputeId);
        if (webhook.transactionReference() != null && !webhook.transactionReference().isBlank()) {
            dispute.setTransactionReference(webhook.transactionReference());
            CheckoutPayment payment = paymentRepository
                    .findByProviderReferenceForUpdate(webhook.transactionReference()).orElse(null);
            if (payment != null) dispute.setPayment(payment);
        }
        PaymentDisputeStatus providerStatus = "charge.dispute.resolve".equals(webhook.event())
                ? PaymentDisputeStatus.RESOLVED : parseStatus(webhook.status());
        if ("charge.dispute.create".equals(webhook.event())
                && providerStatus == PaymentDisputeStatus.UNKNOWN && created) {
            providerStatus = PaymentDisputeStatus.AWAITING_MERCHANT_FEEDBACK;
        }
        if (dispute.getStatus() == PaymentDisputeStatus.RESOLVED
                && providerStatus != PaymentDisputeStatus.RESOLVED) {
            providerStatus = PaymentDisputeStatus.RESOLVED;
        }
        if (providerStatus != PaymentDisputeStatus.UNKNOWN || created) dispute.setStatus(providerStatus);
        if (webhook.disputeResolution() != null && !webhook.disputeResolution().isBlank()) {
            dispute.setResolution(webhook.disputeResolution().trim());
        }
        if (webhook.disputeDueAt() != null) dispute.setDueAt(webhook.disputeDueAt());
        if (webhook.amountMinor() != null) {
            dispute.setDisputedAmount(BigDecimal.valueOf(webhook.amountMinor(), 2));
        }
        if (webhook.currency() != null && webhook.currency().matches("(?i)[a-z]{3}")) {
            dispute.setCurrency(webhook.currency().toUpperCase(Locale.ROOT));
        }
        dispute.setLastEventName(webhook.event());
        dispute.setLastEventAt(OffsetDateTime.now());
        if ("charge.dispute.remind".equals(webhook.event())) {
            dispute.setReminderCount(dispute.getReminderCount() + 1);
        }
        if (dispute.getStatus() != PaymentDisputeStatus.RESOLVED) {
            dispute.setClearedBy(null);
            dispute.setClearedAt(null);
        }
        dispute = disputeRepository.saveAndFlush(dispute);
        if (created || "charge.dispute.remind".equals(webhook.event())) {
            notifySuperAdmins(dispute, created);
        }
    }

    @Transactional(readOnly = true)
    public Page<PaymentDisputeResponse> list(AuthenticatedUser actor, PaymentDisputeStatus status,
                                             Pageable pageable) {
        requireSuperAdmin(actor);
        Page<PaymentDispute> disputes = status == null
                ? disputeRepository.findAllByOrderByLastEventAtDesc(pageable)
                : disputeRepository.findByStatusOrderByDueAtAscLastEventAtDesc(status, pageable);
        return disputes.map(PaymentDisputeResponse::from);
    }

    @Transactional
    public PaymentDisputeResponse review(AuthenticatedUser actor, String providerDisputeId,
                                         UpdatePaymentDisputeReviewRequest request) {
        requireSuperAdmin(actor);
        if (request == null || request.reviewNote() == null || request.reviewNote().isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "A dispute review note is required");
        }
        PaymentDispute dispute = disputeRepository.findByProviderDisputeIdForUpdate(providerDisputeId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Payment dispute not found"));
        dispute.setReviewNote(request.reviewNote().trim());
        if (request.clearForSettlement()) {
            if (dispute.getStatus() != PaymentDisputeStatus.RESOLVED) {
                throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                        "A dispute can be cleared for payout only after Paystack marks it resolved");
            }
            dispute.setClearedBy(userRepository.getReferenceById(actor.userId()));
            dispute.setClearedAt(OffsetDateTime.now());
        }
        return PaymentDisputeResponse.from(disputeRepository.save(dispute));
    }

    private void notifySuperAdmins(PaymentDispute dispute, boolean created) {
        String title = created ? "Payment dispute requires review" : "Payment dispute reminder";
        String message = "Paystack dispute " + dispute.getProviderDisputeId()
                + (dispute.getDueAt() == null ? "" : " is due " + dispute.getDueAt())
                + ". Review it in the financial disputes queue.";
        List<User> admins = userRepository.findByRole(UserRole.SUPER_ADMIN);
        admins.forEach(admin -> notificationService.create(admin, title, message,
                NotificationType.PAYMENT, created ? NotificationPriority.CRITICAL : NotificationPriority.HIGH,
                dispute.getId(), "PAYMENT_DISPUTE"));
    }

    private static PaymentDisputeStatus parseStatus(String status) {
        if (status == null) return PaymentDisputeStatus.UNKNOWN;
        return switch (status.trim().toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "awaiting_merchant_feedback" -> PaymentDisputeStatus.AWAITING_MERCHANT_FEEDBACK;
            case "awaiting_bank_feedback" -> PaymentDisputeStatus.AWAITING_BANK_FEEDBACK;
            case "pending" -> PaymentDisputeStatus.PENDING;
            case "resolved" -> PaymentDisputeStatus.RESOLVED;
            default -> PaymentDisputeStatus.UNKNOWN;
        };
    }

    private static void requireSuperAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }
}
