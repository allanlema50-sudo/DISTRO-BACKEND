package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.PurchaseOrder;
import com.example.distrobackend.Domain.entity.PurchaseOrderSettlement;
import com.example.distrobackend.Domain.entity.PurchaseOrderSettlementPayment;
import com.example.distrobackend.Domain.entity.SettlementProofDocument;
import com.example.distrobackend.Domain.enums.NotificationPriority;
import com.example.distrobackend.Domain.enums.NotificationType;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.PurchaseOrderStatus;
import com.example.distrobackend.Domain.enums.SettlementMethod;
import com.example.distrobackend.Domain.enums.SettlementPaymentStatus;
import com.example.distrobackend.Domain.enums.SettlementStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.ConfirmBankPaymentRequest;
import com.example.distrobackend.repository.PurchaseOrderRepository;
import com.example.distrobackend.repository.PurchaseOrderSettlementPaymentRepository;
import com.example.distrobackend.repository.PurchaseOrderSettlementRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Bank-transfer settlement of purchase orders. The distributor submits a bank
 * reference and proof; the order stays unpaid. Only the supplier's manufacturer
 * administrator can confirm, recording the amount actually received.
 *
 * <p>The money goes to the manufacturer's own bank account, so nothing is
 * posted to provider clearing. Recording a payment that arrived is never gated
 * on fulfilment, dispute or any other order state beyond the order not being
 * already settled, so a later dispute flag can freeze shipment without ever
 * blocking this path.
 */
@Service
@RequiredArgsConstructor
public class BankTransferSettlementService {

    private static final Pattern REFERENCE = Pattern.compile("[A-Z0-9][A-Z0-9 ._/-]{2,99}");
    private static final long MPESA_IN_FLIGHT_MINUTES = 15;

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderSettlementRepository settlementRepository;
    private final PurchaseOrderSettlementPaymentRepository paymentRepository;
    private final SettlementProofDocumentService proofService;
    private final PlatformFeeService platformFeeService;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Records the distributor's bank reference and proof. Returns the order id.
     * Submitting the same idempotency key, or the same reference again on the
     * same order, changes nothing and returns the same result.
     */
    @Transactional
    public UUID submit(AuthenticatedUser actor, UUID purchaseOrderId, String rawReference,
                       MultipartFile proof, String idempotencyKey) {
        if (actor == null || actor.organizationId() == null || actor.role() == null
                || actor.role().organizationtype() != Organizationtype.DISTRIBUTOR) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "A distributor identity is required to submit a payment");
        }
        String key = idempotencyKey == null ? "" : idempotencyKey.trim();
        if (key.isEmpty() || key.length() > 100) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "A valid Idempotency-Key header is required");
        }
        String reference = normalizeReference(rawReference);

        PurchaseOrder order = purchaseOrderRepository.findByIdForPayment(purchaseOrderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Purchase order not found"));
        if (!actor.organizationId().equals(order.getBuyerOrganization().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }

        PurchaseOrderSettlementPayment byKey = paymentRepository.findByIdempotencyKey(key).orElse(null);
        if (byKey != null) {
            if (!byKey.getSettlement().getPurchaseOrder().getId().equals(order.getId())) {
                throw new ApiException(ErrorCode.CONFLICT, "Idempotency key has already been used");
            }
            return order.getId();
        }
        PurchaseOrderSettlementPayment byReference = paymentRepository
                .findBySupplierOrganization_IdAndPayerReference(order.getSupplierOrganization().getId(), reference)
                .orElse(null);
        if (byReference != null) {
            if (byReference.getSettlement().getPurchaseOrder().getId().equals(order.getId())) {
                return order.getId();
            }
            throw new ApiException(ErrorCode.PAYMENT_REFERENCE_IN_USE,
                    "This payment reference has already been used for another order");
        }

        if (order.getCheckout() != null) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Purchase orders in a marketplace checkout are paid through the checkout payment");
        }
        if (order.getStatus() != PurchaseOrderStatus.APPROVED) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "Only approved purchase orders can be paid");
        }
        List<PurchaseOrderSettlement> settlements =
                settlementRepository.findByPurchaseOrderIdOrderByCreatedAtDesc(order.getId());
        if (FulfilmentGate.paidInFull(order, settlements)) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "Purchase order is already paid");
        }
        OffsetDateTime stkCutoff = OffsetDateTime.now().minusMinutes(MPESA_IN_FLIGHT_MINUTES);
        boolean stkInFlight = settlements.stream().anyMatch(s -> s.getMethod() == SettlementMethod.MPESA
                && s.getStatus() == SettlementStatus.PENDING && s.getMpesaCheckoutRequestId() != null
                && s.getCreatedAt() != null && s.getCreatedAt().isAfter(stkCutoff));
        if (stkInFlight) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "An M-Pesa payment request for this order is still pending. Wait for it to finish first");
        }

        SettlementProofDocument document = proofService.store(order,
                userRepository.getReferenceById(actor.userId()), proof);

        PurchaseOrderSettlement settlement = settlements.stream()
                .filter(s -> s.getMethod() == SettlementMethod.BANK_TRANSFER
                        && s.getStatus() == SettlementStatus.PENDING)
                .findFirst().orElseGet(() -> newBankSettlement(order));

        PurchaseOrderSettlementPayment payment = new PurchaseOrderSettlementPayment();
        payment.setSettlement(settlement);
        payment.setSupplierOrganization(order.getSupplierOrganization());
        payment.setMethod(SettlementMethod.BANK_TRANSFER);
        payment.setPayerReference(reference);
        payment.setProofDocument(document);
        payment.setIdempotencyKey(key);
        payment.setStatus(SettlementPaymentStatus.SUBMITTED);
        payment.setSubmittedBy(userRepository.getReferenceById(actor.userId()));
        try {
            paymentRepository.saveAndFlush(payment);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.PAYMENT_REFERENCE_IN_USE,
                    "This payment reference has already been used");
        }

        String message = "The distributor submitted bank transfer reference " + reference + " for purchase order "
                + order.getPurchaseOrderNumber() + ". Confirm it once the funds reach your account.";
        userRepository.findByOrganization_IdAndRole(order.getSupplierOrganization().getId(),
                        UserRole.MANUFACTURER_ADMIN, Pageable.unpaged())
                .forEach(user -> notificationService.create(user, "Bank payment awaiting confirmation", message,
                        NotificationType.ORDER, NotificationPriority.HIGH, order.getId(), "PURCHASE_ORDER"));
        return order.getId();
    }

    /**
     * Confirms a submitted payment and records the amount received. Only the
     * supplier's manufacturer administrator may do this. The amount received must
     * equal the order total.
     * Confirming a reference that is already confirmed with the same amount is
     * a no-op. Returns the order id.
     */
    @Transactional
    public UUID confirm(AuthenticatedUser actor, UUID purchaseOrderId, ConfirmBankPaymentRequest request) {
        if (actor == null || actor.organizationId() == null || actor.role() != UserRole.MANUFACTURER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "Only the supplier's manufacturer administrator may confirm a bank payment");
        }
        PurchaseOrder order = purchaseOrderRepository.findByIdForUpdate(purchaseOrderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Purchase order not found"));
        if (!actor.organizationId().equals(order.getSupplierOrganization().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        String reference = normalizeReference(request.reference());
        BigDecimal received = request.amountReceived();
        if (received == null || received.signum() <= 0 || received.scale() > 2) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "amount_received must be a positive amount with at most two decimals");
        }
        received = received.setScale(2);

        PurchaseOrderSettlementPayment payment = paymentRepository
                .findBySupplierOrganization_IdAndPayerReference(order.getSupplierOrganization().getId(), reference)
                .filter(p -> p.getSettlement().getPurchaseOrder().getId().equals(order.getId()))
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND,
                        "No submitted bank payment with this reference was found on this order"));
        if (payment.getSubmittedBy() != null && payment.getSubmittedBy().getId().equals(actor.userId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "A payer cannot confirm their own payment");
        }
        if (payment.getStatus() == SettlementPaymentStatus.CONFIRMED) {
            if (payment.getAmount() != null && payment.getAmount().compareTo(received) == 0) {
                return order.getId();
            }
            throw new ApiException(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                    "This reference was already confirmed with a different amount");
        }

        PurchaseOrderSettlement settlement = settlementRepository.findByIdForUpdate(payment.getSettlement().getId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Settlement not found"));
        if (settlement.getStatus() != SettlementStatus.PENDING) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "This order is already settled");
        }
        BigDecimal due = settlement.effectiveTotalDue();
        BigDecimal paid = settlement.getAmountPaid() == null ? BigDecimal.ZERO : settlement.getAmountPaid();
        if (received.compareTo(due) != 0) {
            throw new ApiException(ErrorCode.PAYMENT_AMOUNT_MISMATCH,
                    "The amount received must equal the order total of " + due.toPlainString());
        }

        payment.setAmount(received);
        payment.setStatus(SettlementPaymentStatus.CONFIRMED);
        payment.setConfirmedBy(userRepository.getReferenceById(actor.userId()));
        payment.setConfirmedAt(OffsetDateTime.now());
        paymentRepository.save(payment);

        BigDecimal newPaid = paid.add(received);
        settlement.setAmountPaid(newPaid);
        boolean settled = newPaid.compareTo(due) == 0;
        if (settled) {
            settlement.setStatus(SettlementStatus.SETTLED);
            settlement.setSettledAt(OffsetDateTime.now());
            platformFeeService.accrueOnSettlement(settlement);
        }
        settlementRepository.save(settlement);

        if (settled) {
            // Reserves stock once the pay-now settlement commits.
            eventPublisher.publishEvent(PurchaseOrderPaymentVerifiedEvent.purchaseOrder(order.getId()));
        }
        String message = "Bank payment " + reference + " of " + received.toPlainString() + " "
                + settlement.getCurrency() + " was confirmed for purchase order " + order.getPurchaseOrderNumber()
                + (settled ? ". The order is paid in full." : ". " + due.subtract(newPaid).toPlainString()
                + " remains outstanding.");
        userRepository.findByOrganization_IdAndRole(order.getBuyerOrganization().getId(),
                        UserRole.DISTRIBUTOR_ADMIN, Pageable.unpaged())
                .forEach(user -> notificationService.create(user, "Bank payment confirmed", message,
                        NotificationType.ORDER, NotificationPriority.MEDIUM, order.getId(), "PURCHASE_ORDER"));
        return order.getId();
    }

    private PurchaseOrderSettlement newBankSettlement(PurchaseOrder order) {
        PurchaseOrderSettlement settlement = new PurchaseOrderSettlement();
        settlement.setPurchaseOrder(order);
        settlement.setMethod(SettlementMethod.BANK_TRANSFER);
        settlement.setStatus(SettlementStatus.PENDING);
        settlement.setAmount(order.getTotalAmount());
        settlement.setTotalDue(order.getTotalAmount());
        settlement.setCurrency(order.getCurrency());
        return settlementRepository.saveAndFlush(settlement);
    }

    static String normalizeReference(String raw) {
        String reference = raw == null ? "" : raw.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
        if (!REFERENCE.matcher(reference).matches()) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "reference must be 3 to 100 characters: letters, digits, spaces and . _ / -");
        }
        return reference;
    }
}
