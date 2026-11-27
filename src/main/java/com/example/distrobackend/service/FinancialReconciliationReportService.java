package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.CheckoutPayment;
import com.example.distrobackend.Domain.entity.LedgerEntry;
import com.example.distrobackend.Domain.entity.LedgerJournal;
import com.example.distrobackend.Domain.entity.PaymentAllocation;
import com.example.distrobackend.Domain.entity.PaymentDispute;
import com.example.distrobackend.Domain.entity.PaymentRefund;
import com.example.distrobackend.Domain.entity.SupplierSettlement;
import com.example.distrobackend.Domain.enums.CheckoutPaymentStatus;
import com.example.distrobackend.Domain.enums.LedgerEntryDirection;
import com.example.distrobackend.Domain.enums.PaymentProvider;
import com.example.distrobackend.Domain.enums.RefundStatus;
import com.example.distrobackend.Domain.enums.SupplierSettlementStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.FinancialReconciliationItem;
import com.example.distrobackend.repository.CheckoutPaymentRepository;
import com.example.distrobackend.repository.LedgerJournalRepository;
import com.example.distrobackend.repository.PaymentAllocationRepository;
import com.example.distrobackend.repository.PaymentDisputeRepository;
import com.example.distrobackend.repository.PaymentRefundRepository;
import com.example.distrobackend.repository.SupplierSettlementRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.core.TypedPropertyPath;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FinancialReconciliationReportService {
    private static final Set<CheckoutPaymentStatus> INCLUDED_STATUSES = Set.of(
            CheckoutPaymentStatus.SUCCESS, CheckoutPaymentStatus.REFUND_PENDING, CheckoutPaymentStatus.REFUNDED);
    private static final Set<String> LEDGER_EVENTS = Set.of("PAYMENT_CAPTURED", "PAYMENT_REFUNDED");
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final CheckoutPaymentRepository paymentRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final SupplierSettlementRepository settlementRepository;
    private final PaymentRefundRepository refundRepository;
    private final PaymentDisputeRepository disputeRepository;
    private final LedgerJournalRepository journalRepository;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<FinancialReconciliationItem> report(AuthenticatedUser actor, OffsetDateTime from,
                                                     OffsetDateTime to, Pageable pageable) {
        requireSuperAdmin(actor);
        if (from == null || to == null || !from.isBefore(to)
                || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Provide a valid time range no longer than 31 days");
        }
        int page = Math.max(0, pageable.getPageNumber());
        int size = Math.max(1, Math.min(pageable.getPageSize(), 100));
        Pageable bounded = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC,
                TypedPropertyPath.of(CheckoutPayment::getInitiatedAt),
                TypedPropertyPath.of(CheckoutPayment::getId)));
        Page<CheckoutPayment> payments = paymentRepository
                .findByProviderAndDemoFalseAndStatusInAndInitiatedAtGreaterThanEqualAndInitiatedAtLessThan(
                        PaymentProvider.PAYSTACK, INCLUDED_STATUSES, from, to, bounded);
        if (payments.isEmpty()) return payments.map(ignored -> null);

        List<UUID> paymentIds = payments.getContent().stream().map(CheckoutPayment::getId).toList();
        List<PaymentAllocation> allocations = allocationRepository.findByPayment_IdInOrderByCreatedAtAsc(paymentIds);
        List<SupplierSettlement> settlements = settlementRepository.findByAllocation_Payment_IdIn(paymentIds);
        List<PaymentRefund> refunds = refundRepository.findByPayment_IdInOrderByCreatedAtDesc(paymentIds);
        List<String> providerReferences = payments.getContent().stream().map(CheckoutPayment::getProviderReference)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<UUID, PaymentDispute> disputesById = new LinkedHashMap<>();
        disputeRepository.findByPayment_IdIn(paymentIds)
                .forEach(dispute -> disputesById.put(dispute.getId(), dispute));
        if (!providerReferences.isEmpty()) {
            disputeRepository.findByTransactionReferenceIn(providerReferences)
                    .forEach(dispute -> disputesById.putIfAbsent(dispute.getId(), dispute));
        }
        List<PaymentDispute> disputes = List.copyOf(disputesById.values());
        List<UUID> allocationIds = allocations.stream().map(PaymentAllocation::getId).toList();
        List<LedgerJournal> journals = allocationIds.isEmpty() ? List.of()
                : journalRepository.findBySourceTypeAndSourceIdInAndEventTypeIn(
                        "CHECKOUT_ALLOCATION", allocationIds, LEDGER_EVENTS);
        List<UUID> settlementIds = settlements.stream().map(SupplierSettlement::getId).toList();
        List<LedgerJournal> payoutJournals = settlementIds.isEmpty() ? List.of()
                : journalRepository.findBySourceTypeAndSourceIdIn("SUPPLIER_SETTLEMENT", settlementIds);

        Map<UUID, List<PaymentAllocation>> allocationsByPayment = allocations.stream().collect(
                Collectors.groupingBy(item -> item.getPayment().getId()));
        Map<UUID, List<SupplierSettlement>> settlementsByPayment = settlements.stream().collect(
                Collectors.groupingBy(item -> item.getAllocation().getPayment().getId()));
        Map<UUID, List<PaymentRefund>> refundsByPayment = refunds.stream().collect(
                Collectors.groupingBy(item -> item.getPayment().getId()));
        Map<String, UUID> paymentIdByReference = payments.getContent().stream()
                .filter(payment -> payment.getProviderReference() != null).collect(Collectors.toMap(
                CheckoutPayment::getProviderReference, CheckoutPayment::getId, (first, ignored) -> first));
        Map<UUID, List<PaymentDispute>> disputesByPayment = new HashMap<>();
        for (PaymentDispute dispute : disputes) {
            UUID ownerPaymentId = dispute.getPayment() == null ? null : dispute.getPayment().getId();
            if (ownerPaymentId == null || !paymentIds.contains(ownerPaymentId)) {
                ownerPaymentId = paymentIdByReference.get(dispute.getTransactionReference());
            }
            if (ownerPaymentId != null) {
                disputesByPayment.computeIfAbsent(ownerPaymentId, ignored -> new ArrayList<>()).add(dispute);
            }
        }
        Map<UUID, List<LedgerJournal>> journalsByAllocation = journals.stream().collect(
                Collectors.groupingBy(LedgerJournal::getSourceId));
        Map<UUID, List<LedgerJournal>> journalsBySettlement = payoutJournals.stream().collect(
                Collectors.groupingBy(LedgerJournal::getSourceId));
        return payments.map(payment -> build(payment,
                allocationsByPayment.getOrDefault(payment.getId(), List.of()),
                settlementsByPayment.getOrDefault(payment.getId(), List.of()),
                refundsByPayment.getOrDefault(payment.getId(), List.of()),
                disputesByPayment.getOrDefault(payment.getId(), List.of()), journalsByAllocation,
                journalsBySettlement));
    }

    private FinancialReconciliationItem build(CheckoutPayment payment, List<PaymentAllocation> allocations,
                                               List<SupplierSettlement> settlements, List<PaymentRefund> refunds,
                                               List<PaymentDispute> disputes,
                                               Map<UUID, List<LedgerJournal>> journalsByAllocation,
                                               Map<UUID, List<LedgerJournal>> journalsBySettlement) {
        BigDecimal allocationTotal = allocations.stream().map(PaymentAllocation::getGrossAmount)
                .filter(java.util.Objects::nonNull).reduce(ZERO, BigDecimal::add);
        BigDecimal settledTotal = settlements.stream()
                .filter(item -> item.getStatus() == SupplierSettlementStatus.SETTLED)
                .map(SupplierSettlement::getAmount).filter(java.util.Objects::nonNull)
                .reduce(ZERO, BigDecimal::add);
        int captureCount = 0;
        int refundJournalCount = 0;
        int unbalancedCount = 0;
        for (PaymentAllocation allocation : allocations) {
            for (LedgerJournal journal : journalsByAllocation.getOrDefault(allocation.getId(), List.of())) {
                if ("PAYMENT_REFUNDED".equals(journal.getEventType())) {
                    refundJournalCount++;
                    continue;
                }
                if (!"PAYMENT_CAPTURED".equals(journal.getEventType())) continue;
                captureCount++;
                BigDecimal debits = journal.getEntries().stream()
                        .filter(entry -> entry.getDirection() == LedgerEntryDirection.DEBIT)
                        .map(LedgerEntry::getAmount).reduce(ZERO, BigDecimal::add);
                BigDecimal credits = journal.getEntries().stream()
                        .filter(entry -> entry.getDirection() == LedgerEntryDirection.CREDIT)
                        .map(LedgerEntry::getAmount).reduce(ZERO, BigDecimal::add);
                if (debits.compareTo(credits) != 0 || debits.signum() <= 0) unbalancedCount++;
            }
        }
        int payoutJournalCount = 0;
        int reversedPayoutJournalCount = 0;
        int unbalancedPayoutJournalCount = 0;
        Set<String> issues = new LinkedHashSet<>();
        for (SupplierSettlement settlement : settlements) {
            List<LedgerJournal> settlementJournals = journalsBySettlement
                    .getOrDefault(settlement.getId(), List.of());
            Set<String> reversedEvents = settlementJournals.stream().map(LedgerJournal::getEventType)
                    .filter(event -> event.startsWith("PAYOUT_REVERSED_"))
                    .collect(Collectors.toSet());
            List<LedgerJournal> payoutEvents = settlementJournals.stream()
                    .filter(journal -> journal.getEventType().startsWith("PAYOUT_SETTLED_"))
                    .toList();
            Map<String, LedgerJournal> payoutByEvent = payoutEvents.stream().collect(Collectors.toMap(
                    LedgerJournal::getEventType, journal -> journal));
            long openPayouts = payoutEvents.stream().filter(journal -> !reversedEvents.contains(
                    journal.getEventType().replace("PAYOUT_SETTLED_", "PAYOUT_REVERSED_"))).count();
            payoutJournalCount += payoutEvents.size();
            reversedPayoutJournalCount += reversedEvents.size();
            if (settlement.getStatus() == SupplierSettlementStatus.SETTLED && openPayouts != 1) {
                issues.add("SETTLED_PAYOUT_LEDGER_COUNT_MISMATCH");
            }
            if (settlement.getStatus() == SupplierSettlementStatus.REVERSED
                    && (payoutEvents.isEmpty() || reversedEvents.size() != payoutEvents.size() || openPayouts != 0)) {
                issues.add("REVERSED_PAYOUT_LEDGER_MISMATCH");
            }
            for (LedgerJournal journal : settlementJournals) {
                if (!journal.getEventType().startsWith("PAYOUT_SETTLED_")
                        && !journal.getEventType().startsWith("PAYOUT_REVERSED_")) continue;
                BigDecimal debits = journal.getEntries().stream()
                        .filter(entry -> entry.getDirection() == LedgerEntryDirection.DEBIT)
                        .map(LedgerEntry::getAmount).reduce(ZERO, BigDecimal::add);
                BigDecimal credits = journal.getEntries().stream()
                        .filter(entry -> entry.getDirection() == LedgerEntryDirection.CREDIT)
                        .map(LedgerEntry::getAmount).reduce(ZERO, BigDecimal::add);
                if (debits.compareTo(credits) != 0 || debits.signum() <= 0) {
                    unbalancedPayoutJournalCount++;
                }
                if (journal.getEventType().startsWith("PAYOUT_SETTLED_")
                        && (settlement.getAmount() == null || debits.compareTo(settlement.getAmount()) != 0
                        || settlement.getCurrency() == null
                        || !settlement.getCurrency().equalsIgnoreCase(journal.getCurrency()))) {
                    issues.add("PAYOUT_JOURNAL_AMOUNT_MISMATCH");
                }
                if (journal.getEventType().startsWith("PAYOUT_REVERSED_")) {
                    String payoutEvent = journal.getEventType().replace("PAYOUT_REVERSED_", "PAYOUT_SETTLED_");
                    LedgerJournal originalPayout = payoutByEvent.get(payoutEvent);
                    BigDecimal originalDebits = originalPayout == null ? ZERO : originalPayout.getEntries().stream()
                            .filter(entry -> entry.getDirection() == LedgerEntryDirection.DEBIT)
                            .map(LedgerEntry::getAmount).reduce(ZERO, BigDecimal::add);
                    if (originalPayout == null || debits.compareTo(originalDebits) != 0
                            || !journal.getCurrency().equalsIgnoreCase(originalPayout.getCurrency())) {
                        issues.add("PAYOUT_REVERSAL_AMOUNT_MISMATCH");
                    }
                }
            }
        }
        PaymentRefund latestRefund = refunds.stream().max(java.util.Comparator
                .comparing(PaymentRefund::getCreatedAt,
                        java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder()))
                .thenComparing(PaymentRefund::getId,
                        java.util.Comparator.nullsFirst(java.util.Comparator.naturalOrder())))
                .orElse(null);
        if (allocations.isEmpty()) issues.add("MISSING_ALLOCATIONS");
        if (!allocations.isEmpty() && allocationTotal.compareTo(payment.getAmount()) != 0) {
            issues.add("ALLOCATION_TOTAL_MISMATCH");
        }
        if (captureCount != allocations.size()) issues.add("CAPTURE_JOURNAL_COUNT_MISMATCH");
        if (unbalancedCount > 0) issues.add("UNBALANCED_CAPTURE_JOURNAL");
        if (unbalancedPayoutJournalCount > 0) issues.add("UNBALANCED_PAYOUT_JOURNAL");
        if (!allocations.isEmpty() && settlements.size() != allocations.size()) {
            issues.add("SETTLEMENT_COUNT_MISMATCH");
        }
        if (payment.getStatus() == CheckoutPaymentStatus.REFUNDED
                && (latestRefund == null || latestRefund.getStatus() != RefundStatus.PROCESSED)) {
            issues.add("REFUNDED_PAYMENT_WITHOUT_PROCESSED_REFUND");
        }
        if (latestRefund != null && latestRefund.getStatus() == RefundStatus.PROCESSED
                && payment.getStatus() != CheckoutPaymentStatus.REFUNDED) {
            issues.add("PROCESSED_REFUND_PAYMENT_NOT_REFUNDED");
        }
        if (payment.getStatus() == CheckoutPaymentStatus.REFUNDED
                && refundJournalCount != allocations.size()) {
            issues.add("REFUND_LEDGER_COUNT_MISMATCH");
        }
        long unclearedDisputes = disputes.stream().filter(dispute -> dispute.getClearedAt() == null).count();
        if (unclearedDisputes > 0) issues.add("DISPUTE_REVIEW_REQUIRED");
        return new FinancialReconciliationItem(payment.getId(), payment.getCheckout().getCheckoutReference(),
                payment.getProvider() == null ? null : payment.getProvider().name(), payment.getProviderMode(),
                payment.getProviderReference(), payment.getStatus().name(), payment.getAmount(),
                payment.getCurrency(), payment.getInitiatedAt(), allocationTotal, allocations.size(),
                captureCount, refundJournalCount, unbalancedCount, settlements.size(), settledTotal,
                payoutJournalCount, reversedPayoutJournalCount, unbalancedPayoutJournalCount,
                latestRefund == null ? null : latestRefund.getStatus().name(), disputes.size(), List.copyOf(issues));
    }

    private static void requireSuperAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }
}
