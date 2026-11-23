package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.*;
import com.example.distrobackend.Domain.enums.*;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.*;
import com.example.distrobackend.repository.*;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PaystackSettlementService {
    private final PaystackPaymentService paystackPaymentService;
    private final PayoutBeneficiaryRepository beneficiaryRepository;
    private final SettlementBatchRepository batchRepository;
    private final SupplierSettlementRepository settlementRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final PaymentDisputeRepository disputeRepository;
    private final CheckoutPaymentRepository checkoutPaymentRepository;
    private final FinancialLedgerService financialLedgerService;
    private final SupplierPayoutAttemptRepository payoutAttemptRepository;

    @Transactional
    public PayoutBeneficiaryResponse createBeneficiary(AuthenticatedUser actor,
                                                       CreatePayoutBeneficiaryRequest request) {
        requireOrganizationAdmin(actor);
        PayoutMethod method = parseMethod(request.method());
        String currency = normalizeCurrency(request.currency());
        String accountName = requireText(request.accountName(), "accountName");
        String accountNumber = method == PayoutMethod.MOBILE_MONEY
                ? normalizeMobile(request.mobileNumber()) : requireDigits(request.accountNumber(), "accountNumber");
        String bankCode = clean(request.bankCode());
        if (method == PayoutMethod.BANK_ACCOUNT && bankCode == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "bankCode is required for a bank beneficiary");
        }
        Organization organization = organizationRepository.findById(actor.organizationId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Organization not found"));
        PaystackPaymentService.Recipient recipient = paystackPaymentService.createRecipient(
                method, accountName, accountNumber, bankCode, currency, clean(request.email()));

        PayoutBeneficiary beneficiary = new PayoutBeneficiary();
        beneficiary.setOrganization(organization);
        beneficiary.setMethod(method);
        beneficiary.setProvider(PaymentProvider.PAYSTACK);
        beneficiary.setCurrency(currency);
        beneficiary.setAccountName(accountName);
        beneficiary.setAccountMask(mask(accountNumber));
        beneficiary.setBankCode(bankCode);
        beneficiary.setProviderRecipientCode(recipient.recipientCode());
        beneficiary.setStatus(PayoutBeneficiaryStatus.PENDING_REVIEW);
        beneficiary.setCreatedBy(userRepository.getReferenceById(actor.userId()));
        beneficiary.setReviewNote("Submitted for platform review");
        return PayoutBeneficiaryResponse.from(beneficiaryRepository.save(beneficiary));
    }

    @Transactional(readOnly = true)
    public Page<PayoutBeneficiaryResponse> listOwnBeneficiaries(AuthenticatedUser actor, Pageable pageable) {
        requireOrganization(actor);
        return beneficiaryRepository.findByOrganization_IdOrderByCreatedAtDesc(actor.organizationId(), pageable)
                .map(PayoutBeneficiaryResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<PayoutBeneficiaryResponse> listBeneficiaries(AuthenticatedUser actor, Pageable pageable) {
        requirePlatformAdmin(actor);
        return beneficiaryRepository.findAll(pageable).map(PayoutBeneficiaryResponse::from);
    }

    @Transactional
    public PayoutBeneficiaryResponse reviewBeneficiary(AuthenticatedUser actor, UUID beneficiaryId,
                                                       ReviewPayoutBeneficiaryRequest request) {
        requirePlatformAdmin(actor);
        PayoutBeneficiary beneficiary = beneficiaryRepository.findByIdForUpdate(beneficiaryId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Payout beneficiary not found"));
        String note = clean(request.note());
        if (Boolean.TRUE.equals(request.approve())) {
            beneficiaryRepository.findByOrganization_IdAndStatusAndCurrency(
                            beneficiary.getOrganization().getId(), PayoutBeneficiaryStatus.ACTIVE,
                            beneficiary.getCurrency())
                    .filter(existing -> !existing.getId().equals(beneficiaryId))
                    .ifPresent(existing -> {
                        throw new ApiException(ErrorCode.CONFLICT,
                                "The organization already has an active beneficiary for this currency");
                    });
            beneficiary.setStatus(PayoutBeneficiaryStatus.ACTIVE);
        } else {
            beneficiary.setStatus(PayoutBeneficiaryStatus.REJECTED);
        }
        beneficiary.setReviewedBy(userRepository.getReferenceById(actor.userId()));
        beneficiary.setReviewedAt(OffsetDateTime.now());
        beneficiary.setReviewNote(note == null
                ? (Boolean.TRUE.equals(request.approve()) ? "Beneficiary approved" : "Beneficiary rejected") : note);
        return PayoutBeneficiaryResponse.from(beneficiaryRepository.save(beneficiary));
    }

    @Transactional(readOnly = true)
    public Page<SupplierSettlementAdminResponse> listSettlements(AuthenticatedUser actor,
                                                                 SupplierSettlementStatus status,
                                                                 Pageable pageable) {
        requirePlatformAdmin(actor);
        SupplierSettlementStatus requested = status == null ? SupplierSettlementStatus.PENDING : status;
        return settlementRepository.findByStatusOrderByCreatedAtAsc(requested, pageable)
                .map(SupplierSettlementAdminResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<SupplierPayoutAttemptResponse> listPayoutAttempts(AuthenticatedUser actor,
                                                                  UUID settlementId,
                                                                  Pageable pageable) {
        requirePlatformAdmin(actor);
        if (!settlementRepository.existsById(settlementId)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Supplier settlement not found");
        }
        int page = Math.max(0, pageable.getPageNumber());
        int size = Math.max(1, Math.min(pageable.getPageSize(), 100));
        return payoutAttemptRepository.findBySettlement_IdOrderByAttemptNumberDesc(
                        settlementId, org.springframework.data.domain.PageRequest.of(page, size))
                .map(SupplierPayoutAttemptResponse::from);
    }

    @Transactional
    public SettlementBatchResponse createBatch(AuthenticatedUser actor, CreateSettlementBatchRequest request) {
        requirePlatformAdmin(actor);
        List<UUID> ids = request.settlementIds().stream().distinct().toList();
        if (ids.size() != request.settlementIds().size()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Settlement IDs must be unique");
        }
        List<SupplierSettlement> settlements = ids.stream()
                .map(id -> settlementRepository.findByIdForUpdate(id)
                        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Supplier settlement not found")))
                .toList();
        String currency = null;
        BigDecimal total = BigDecimal.ZERO;
        for (SupplierSettlement settlement : settlements) {
            validateBatchCandidate(settlement);
            if (currency == null) currency = normalizeCurrency(settlement.getCurrency());
            if (!currency.equalsIgnoreCase(settlement.getCurrency())) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "A settlement batch cannot mix currencies");
            }
            PayoutBeneficiary beneficiary = beneficiaryRepository
                    .findByOrganization_IdAndStatusAndCurrency(settlement.getSupplierOrganization().getId(),
                            PayoutBeneficiaryStatus.ACTIVE, currency)
                    .orElseThrow(() -> new ApiException(ErrorCode.CONFLICT,
                            "Supplier organization has no active payout beneficiary"));
            settlement.setBeneficiary(beneficiary);
            settlement.setProvider(PaymentProvider.PAYSTACK);
            settlement.setPayoutReference("lf_settlement_" + UUID.randomUUID().toString().replace("-", ""));
            total = total.add(settlement.getAmount());
        }
        SettlementBatch batch = new SettlementBatch();
        batch.setProvider(PaymentProvider.PAYSTACK);
        batch.setStatus(SettlementBatchStatus.PENDING_APPROVAL);
        batch.setCurrency(currency);
        batch.setTotalAmount(total);
        batch.setSettlementCount(settlements.size());
        batch.setCreatedBy(userRepository.getReferenceById(actor.userId()));
        batch.setNote(clean(request.note()));
        batch = batchRepository.saveAndFlush(batch);
        for (SupplierSettlement settlement : settlements) {
            settlement.setSettlementBatch(batch);
            settlementRepository.save(settlement);
        }
        return SettlementBatchResponse.from(batch);
    }

    @Transactional(readOnly = true)
    public Page<SettlementBatchResponse> listBatches(AuthenticatedUser actor, Pageable pageable) {
        requirePlatformAdmin(actor);
        return batchRepository.findAllByOrderByCreatedAtDesc(pageable).map(SettlementBatchResponse::from);
    }

    @Transactional
    public SettlementBatchResponse approveBatch(AuthenticatedUser actor, UUID batchId) {
        requirePlatformAdmin(actor);
        SettlementBatch batch = lockBatch(batchId);
        if (batch.getStatus() != SettlementBatchStatus.PENDING_APPROVAL) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "Only a pending settlement batch can be approved");
        }
        batch.setStatus(SettlementBatchStatus.APPROVED);
        batch.setApprovedBy(userRepository.getReferenceById(actor.userId()));
        batch.setApprovedAt(OffsetDateTime.now());
        return SettlementBatchResponse.from(batchRepository.save(batch));
    }

    @Transactional
    public SettlementBatchResponse executeBatch(AuthenticatedUser actor, UUID batchId) {
        requirePlatformAdmin(actor);
        SettlementBatch batch = lockBatch(batchId);
        if (batch.getStatus() != SettlementBatchStatus.APPROVED) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "Only an approved settlement batch can be executed");
        }
        List<SupplierSettlement> batchSettlements = settlementRepository
                .findBySettlementBatch_IdOrderByCreatedAtAsc(batchId);
        if (batchSettlements.stream().anyMatch(this::hasUnclearedDispute)) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "A settlement in this batch is blocked by an unresolved or unreviewed payment dispute");
        }
        batch.setStatus(SettlementBatchStatus.PROCESSING);
        batchRepository.save(batch);
        for (SupplierSettlement settlement : batchSettlements) {
            executeSettlement(settlement);
        }
        refreshBatchStatus(batch);
        batch.setExecutedAt(OffsetDateTime.now());
        return SettlementBatchResponse.from(batchRepository.save(batch));
    }

    @Transactional
    public SupplierSettlementAdminResponse retrySettlement(AuthenticatedUser actor, UUID settlementId) {
        requirePlatformAdmin(actor);
        SupplierSettlement settlement = settlementRepository.findByIdForUpdate(settlementId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Supplier settlement not found"));
        if (settlement.getStatus() != SupplierSettlementStatus.FAILED
                && settlement.getStatus() != SupplierSettlementStatus.REVERSED) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Only a failed or reversed settlement can be retried");
        }
        if (hasUnclearedDispute(settlement)) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "This settlement is blocked by an unresolved or unreviewed payment dispute");
        }
        executeSettlement(settlement);
        return SupplierSettlementAdminResponse.from(settlementRepository.save(settlement));
    }

    @Transactional
    public void processPaystackWebhook(PaystackPaymentService.Webhook webhook) {
        if (!Set.of("transfer.success", "transfer.failed", "transfer.reversed").contains(webhook.event())) return;
        SupplierPayoutAttempt attempt = payoutAttemptRepository
                .findByProviderReferenceForUpdate(webhook.reference()).orElse(null);
        if (attempt == null) return;
        SupplierSettlement settlement = settlementRepository.findByIdForUpdate(attempt.getSettlement().getId())
                .orElse(null);
        if (settlement == null) return;
        if (settlement.getProvider() != PaymentProvider.PAYSTACK) return;
        if (("transfer.success".equals(webhook.event())
                && attempt.getStatus() == SupplierPayoutAttemptStatus.SUCCEEDED)
                || ("transfer.failed".equals(webhook.event())
                && attempt.getStatus() == SupplierPayoutAttemptStatus.FAILED)
                || ("transfer.reversed".equals(webhook.event())
                && attempt.getStatus() == SupplierPayoutAttemptStatus.REVERSED)) {
            return;
        }
        if (webhook.transferCode() != null) {
            attempt.setProviderTransferCode(webhook.transferCode());
            settlement.setProviderTransferCode(webhook.transferCode());
        }
        if (webhook.amountMinor() != null || webhook.currency() != null) {
            long expected = webhookAmount(attempt.getAmount(), attempt.getCurrency());
            if (webhook.amountMinor() == null || webhook.currency() == null
                    || webhook.amountMinor() != expected || !attempt.getCurrency().equalsIgnoreCase(webhook.currency())) {
                if (attempt.getStatus() == SupplierPayoutAttemptStatus.SUCCEEDED
                        || attempt.getStatus() == SupplierPayoutAttemptStatus.REVERSED) return;
                attempt.setStatus(SupplierPayoutAttemptStatus.OUTCOME_UNKNOWN);
                attempt.setFailureReason("Paystack transfer amount or currency mismatch; manual reconciliation required");
                attempt.setCompletedAt(OffsetDateTime.now());
                payoutAttemptRepository.save(attempt);
                if (attempt.getAttemptNumber() == settlement.getPayoutAttempts()
                        && settlement.getStatus() != SupplierSettlementStatus.SETTLED) {
                    settlement.setStatus(SupplierSettlementStatus.PROCESSING);
                    settlement.setFailureReason(attempt.getFailureReason());
                    settlement.setNextReconciliationAt(OffsetDateTime.now().plusMinutes(1));
                }
                settlementRepository.save(settlement);
                refreshBatchStatus(settlement.getSettlementBatch());
                return;
            }
        }
        switch (webhook.event()) {
            case "transfer.success" -> {
                if (attempt.getStatus() == SupplierPayoutAttemptStatus.REVERSED) return;
                financialLedgerService.recordSupplierPayout(settlement, attempt.getAttemptNumber());
                attempt.setStatus(SupplierPayoutAttemptStatus.SUCCEEDED);
                attempt.setCompletedAt(OffsetDateTime.now());
                attempt.setFailureReason(null);
                settlement.setStatus(SupplierSettlementStatus.SETTLED);
                settlement.setSettledAt(OffsetDateTime.now());
                settlement.setFailureReason(null);
                settlement.setNextReconciliationAt(null);
            }
            case "transfer.failed" -> {
                if (attempt.getStatus() != SupplierPayoutAttemptStatus.SUCCEEDED
                        && attempt.getStatus() != SupplierPayoutAttemptStatus.REVERSED) {
                    attempt.setStatus(SupplierPayoutAttemptStatus.FAILED);
                    attempt.setCompletedAt(OffsetDateTime.now());
                    attempt.setFailureReason(webhook.gatewayResponse() == null
                            ? "Paystack reported a failed transfer" : webhook.gatewayResponse());
                    if (attempt.getAttemptNumber() == settlement.getPayoutAttempts()
                            && settlement.getStatus() != SupplierSettlementStatus.SETTLED) {
                        settlement.setStatus(SupplierSettlementStatus.FAILED);
                        settlement.setFailureReason(attempt.getFailureReason());
                    }
                }
            }
            case "transfer.reversed" -> {
                financialLedgerService.recordSupplierPayout(settlement, attempt.getAttemptNumber());
                financialLedgerService.recordSupplierPayoutReversal(settlement, attempt.getAttemptNumber());
                attempt.setStatus(SupplierPayoutAttemptStatus.REVERSED);
                attempt.setCompletedAt(OffsetDateTime.now());
                attempt.setFailureReason("Paystack reversed the transfer");
                if (attempt.getAttemptNumber() == settlement.getPayoutAttempts()) {
                    settlement.setStatus(SupplierSettlementStatus.REVERSED);
                    settlement.setFailureReason(attempt.getFailureReason());
                }
            }
            default -> { return; }
        }
        payoutAttemptRepository.save(attempt);
        settlementRepository.save(settlement);
        refreshBatchStatus(settlement.getSettlementBatch());
    }

    private void executeSettlement(SupplierSettlement settlement) {
        if (settlement.getStatus() == SupplierSettlementStatus.SETTLED) return;
        if (settlement.getBeneficiary() == null
                || settlement.getBeneficiary().getStatus() != PayoutBeneficiaryStatus.ACTIVE) {
            settlement.setStatus(SupplierSettlementStatus.FAILED);
            settlement.setFailureReason("No active payout beneficiary is attached");
            return;
        }
        int attemptNumber = Math.toIntExact(payoutAttemptRepository
                .findTopBySettlement_IdOrderByAttemptNumberDesc(settlement.getId())
                .map(previous -> (long) previous.getAttemptNumber() + 1).orElse(1L));
        String reference = "lfp_" + settlement.getId().toString().replace("-", "") + "_" + attemptNumber;
        OffsetDateTime now = OffsetDateTime.now();
        SupplierPayoutAttempt attempt = new SupplierPayoutAttempt();
        attempt.setSettlement(settlement);
        attempt.setAttemptNumber(attemptNumber);
        attempt.setProviderReference(reference);
        attempt.setStatus(SupplierPayoutAttemptStatus.PROCESSING);
        attempt.setAmount(settlement.getAmount());
        attempt.setCurrency(settlement.getCurrency());
        attempt.setStartedAt(now);
        attempt = payoutAttemptRepository.saveAndFlush(attempt);
        settlement.setPayoutReference(reference);
        settlement.setStatus(SupplierSettlementStatus.PROCESSING);
        settlement.setPayoutAttempts(attemptNumber);
        settlement.setLastAttemptAt(now);
        settlement.setNextReconciliationAt(now.plusMinutes(1));
        try {
            PaystackPaymentService.Transfer transfer = paystackPaymentService.initiateTransfer(
                    attempt.getProviderReference(), settlement.getAmount(), settlement.getCurrency(),
                    settlement.getBeneficiary().getProviderRecipientCode(),
                    "LogiFlow supplier settlement " + settlement.getId());
            attempt.setProviderTransferCode(transfer.transferCode());
            settlement.setProviderTransferCode(transfer.transferCode());
            settlement.setFailureReason(null);
            if ("success".equalsIgnoreCase(transfer.status())) {
                financialLedgerService.recordSupplierPayout(settlement, attemptNumber);
                attempt.setStatus(SupplierPayoutAttemptStatus.SUCCEEDED);
                attempt.setCompletedAt(OffsetDateTime.now());
                settlement.setStatus(SupplierSettlementStatus.SETTLED);
                settlement.setSettledAt(OffsetDateTime.now());
                settlement.setNextReconciliationAt(null);
            } else if ("failed".equalsIgnoreCase(transfer.status())) {
                attempt.setStatus(SupplierPayoutAttemptStatus.FAILED);
                attempt.setFailureReason("Paystack reported a failed transfer");
                attempt.setCompletedAt(OffsetDateTime.now());
                settlement.setStatus(SupplierSettlementStatus.FAILED);
                settlement.setFailureReason(attempt.getFailureReason());
            }
        } catch (PaystackPaymentService.ProviderOutcomeUnknownException ex) {
            attempt.setStatus(SupplierPayoutAttemptStatus.OUTCOME_UNKNOWN);
            attempt.setFailureReason("Paystack transfer outcome is unknown; reconciliation is required");
            settlement.setFailureReason("Paystack transfer outcome is unknown; reconciliation is required");
        } catch (ApiException ex) {
            attempt.setStatus(SupplierPayoutAttemptStatus.FAILED);
            attempt.setFailureReason(ex.getMessage());
            attempt.setCompletedAt(OffsetDateTime.now());
            settlement.setStatus(SupplierSettlementStatus.FAILED);
            settlement.setFailureReason(ex.getMessage());
        }
        payoutAttemptRepository.save(attempt);
        settlementRepository.save(settlement);
    }

    private void validateBatchCandidate(SupplierSettlement settlement) {
        if (settlement.isDemo() || settlement.getStatus() != SupplierSettlementStatus.PENDING
                || settlement.getSettlementBatch() != null || settlement.getAmount() == null
                || settlement.getAmount().signum() <= 0) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Only non-demo, unbatched pending settlements can be selected");
        }
        if (hasUnclearedDispute(settlement)) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "This settlement is blocked by an unresolved or unreviewed payment dispute");
        }
    }

    private boolean hasUnclearedDispute(SupplierSettlement settlement) {
        UUID paymentId = settlement.getAllocation().getPayment().getId();
        checkoutPaymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout payment not found"));
        return disputeRepository.existsByPayment_IdAndClearedAtIsNull(paymentId);
    }

    private SettlementBatch lockBatch(UUID batchId) {
        return batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Settlement batch not found"));
    }

    private void refreshBatchStatus(SettlementBatch batch) {
        if (batch == null) return;
        List<SupplierSettlement> settlements = settlementRepository
                .findBySettlementBatch_IdOrderByCreatedAtAsc(batch.getId());
        if (settlements.isEmpty()) return;
        boolean allSettled = settlements.stream().allMatch(s -> s.getStatus() == SupplierSettlementStatus.SETTLED);
        boolean anyFailed = settlements.stream().anyMatch(s -> s.getStatus() == SupplierSettlementStatus.FAILED
                || s.getStatus() == SupplierSettlementStatus.REVERSED);
        batch.setStatus(allSettled ? SettlementBatchStatus.COMPLETED
                : anyFailed ? SettlementBatchStatus.PARTIALLY_FAILED : SettlementBatchStatus.PROCESSING);
        batchRepository.save(batch);
    }

    private static long webhookAmount(BigDecimal amount, String currency) {
        if (!"KES".equalsIgnoreCase(currency)) throw new ApiException(ErrorCode.BAD_REQUEST, "Settlement currency must be KES");
        try {
            return amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Settlement amount must have at most two decimals");
        }
    }

    private static PayoutMethod parseMethod(String value) {
        try {
            return PayoutMethod.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "method must be MOBILE_MONEY or BANK_ACCOUNT");
        }
    }

    private static String normalizeMobile(String value) {
        String number = requireDigits(value, "mobileNumber");
        if (!number.matches("254[17][0-9]{8}")) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "mobileNumber must use 254XXXXXXXXX format");
        }
        return number;
    }

    private static String requireDigits(String value, String field) {
        String clean = value == null ? "" : value.replaceAll("[^0-9]", "");
        if (clean.isBlank() || clean.length() > 40) throw new ApiException(ErrorCode.BAD_REQUEST, field + " is invalid");
        return clean;
    }

    private static String normalizeCurrency(String value) {
        if (value == null || !"KES".equalsIgnoreCase(value.trim())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Only KES settlement accounts are supported");
        }
        return "KES";
    }

    private static String requireText(String value, String field) {
        String clean = clean(value);
        if (clean == null) throw new ApiException(ErrorCode.BAD_REQUEST, field + " is required");
        return clean;
    }

    private static String mask(String value) {
        return "****" + value.substring(Math.max(0, value.length() - 4));
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static void requireOrganization(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null || actor.role() == null
                || !Set.of(UserRole.MANUFACTURER_ADMIN, UserRole.DISTRIBUTOR_ADMIN).contains(actor.role())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }

    private static void requireOrganizationAdmin(AuthenticatedUser actor) {
        requireOrganization(actor);
    }

    private static void requirePlatformAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) throw new ApiException(ErrorCode.ACCESS_DENIED);
    }
}
