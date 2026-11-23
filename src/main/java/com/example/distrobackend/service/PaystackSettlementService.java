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
        PaystackPaymentService.Recipient recipient;
        try {
            recipient = paystackPaymentService.createRecipient(
                    method, accountName, accountNumber, bankCode, currency, clean(request.email()));
        } catch (PaystackPaymentService.ProviderOutcomeUnknownException ex) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED,
                    "Paystack recipient creation could not be confirmed; check Paystack before submitting again");
        }

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
        batch.setStatus(SettlementBatchStatus.PROCESSING);
        batchRepository.save(batch);
        for (SupplierSettlement settlement : settlementRepository.findBySettlementBatchIdForUpdate(batchId)) {
            executeSettlement(settlement);
        }
        refreshBatchStatus(batch);
        batch.setExecutedAt(OffsetDateTime.now());
        return SettlementBatchResponse.from(batchRepository.save(batch));
    }

    @Transactional
    public SupplierSettlementAdminResponse retrySettlement(AuthenticatedUser actor, UUID settlementId) {
        requirePlatformAdmin(actor);
        SupplierSettlement observed = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Supplier settlement not found"));
        if (observed.getSettlementBatch() == null) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "A settlement must belong to a batch before it can be retried");
        }
        SettlementBatch batch = lockBatch(observed.getSettlementBatch().getId());
        SupplierSettlement settlement = settlementRepository.findByIdForUpdate(settlementId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Supplier settlement not found"));
        if (settlement.getStatus() != SupplierSettlementStatus.FAILED
                && settlement.getStatus() != SupplierSettlementStatus.REVERSED) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Only a failed or reversed settlement can be retried");
        }
        executeSettlement(settlement);
        settlementRepository.save(settlement);
        refreshBatchStatus(batch);
        return SupplierSettlementAdminResponse.from(settlement);
    }

    @Transactional
    public void processPaystackWebhook(PaystackPaymentService.Webhook webhook) {
        if (!Set.of("transfer.success", "transfer.failed", "transfer.reversed").contains(webhook.event())) return;
        SupplierSettlement observed = settlementRepository.findByPayoutReference(webhook.reference()).orElse(null);
        if (observed == null || observed.getSettlementBatch() == null) return;
        SettlementBatch batch = lockBatch(observed.getSettlementBatch().getId());
        SupplierSettlement settlement = settlementRepository.findByPayoutReferenceForUpdate(webhook.reference()).orElse(null);
        if (settlement == null || settlement.getProvider() != PaymentProvider.PAYSTACK) return;
        boolean reversalOfSettled = "transfer.reversed".equals(webhook.event())
                && settlement.getStatus() == SupplierSettlementStatus.SETTLED;
        if (settlement.getStatus() != SupplierSettlementStatus.PROCESSING && !reversalOfSettled) return;
        if (webhook.transferCode() != null) settlement.setProviderTransferCode(webhook.transferCode());
        long expected = webhookAmount(settlement.getAmount(), settlement.getCurrency());
        if (webhook.amountMinor() == null || webhook.currency() == null
                || webhook.amountMinor() != expected
                || !settlement.getCurrency().equalsIgnoreCase(webhook.currency())) {
            settlement.setStatus(SupplierSettlementStatus.FAILED);
            settlement.setFailureReason("Paystack transfer amount or currency was missing or mismatched");
            settlementRepository.save(settlement);
            refreshBatchStatus(batch);
            return;
        }
        switch (webhook.event()) {
            case "transfer.success" -> {
                settlement.setStatus(SupplierSettlementStatus.SETTLED);
                settlement.setSettledAt(OffsetDateTime.now());
                settlement.setFailureReason(null);
            }
            case "transfer.failed" -> {
                settlement.setStatus(SupplierSettlementStatus.FAILED);
                settlement.setFailureReason(webhook.gatewayResponse() == null
                        ? "Paystack reported a failed transfer" : webhook.gatewayResponse());
            }
            case "transfer.reversed" -> {
                settlement.setStatus(SupplierSettlementStatus.REVERSED);
                settlement.setFailureReason("Paystack reversed the transfer");
            }
            default -> { return; }
        }
        settlementRepository.save(settlement);
        refreshBatchStatus(batch);
    }

    @Transactional
    public Optional<String> claimTransferVerification(UUID settlementId, OffsetDateTime attemptCutoff,
                                                      OffsetDateTime checkedBefore, OffsetDateTime checkedAt) {
        SupplierSettlement settlement = settlementRepository.findByIdForUpdate(settlementId).orElse(null);
        if (settlement == null || settlement.getProvider() != PaymentProvider.PAYSTACK
                || settlement.getStatus() != SupplierSettlementStatus.PROCESSING
                || settlement.getPayoutReference() == null || settlement.getLastAttemptAt() == null
                || settlement.getLastAttemptAt().isAfter(attemptCutoff)
                || settlement.getLastStatusCheckAt() != null
                && settlement.getLastStatusCheckAt().isAfter(checkedBefore)) {
            return Optional.empty();
        }
        settlement.setLastStatusCheckAt(checkedAt);
        settlementRepository.saveAndFlush(settlement);
        return Optional.of(settlement.getPayoutReference());
    }

    @Transactional
    public void markPaystackTransferNotFound(String reference) {
        SupplierSettlement observed = settlementRepository.findByPayoutReference(reference).orElse(null);
        if (observed == null || observed.getSettlementBatch() == null) return;
        SettlementBatch batch = lockBatch(observed.getSettlementBatch().getId());
        SupplierSettlement settlement = settlementRepository.findByPayoutReferenceForUpdate(reference).orElse(null);
        if (settlement == null || settlement.getProvider() != PaymentProvider.PAYSTACK
                || settlement.getStatus() != SupplierSettlementStatus.PROCESSING) return;
        settlement.setStatus(SupplierSettlementStatus.FAILED);
        settlement.setFailureReason("Paystack has no transfer for the stored payout reference");
        settlementRepository.save(settlement);
        refreshBatchStatus(batch);
    }

    private void executeSettlement(SupplierSettlement settlement) {
        if (settlement.getStatus() == SupplierSettlementStatus.SETTLED) return;
        if (settlement.getBeneficiary() == null
                || settlement.getBeneficiary().getStatus() != PayoutBeneficiaryStatus.ACTIVE) {
            settlement.setStatus(SupplierSettlementStatus.FAILED);
            settlement.setFailureReason("No active payout beneficiary is attached");
            return;
        }
        settlement.setStatus(SupplierSettlementStatus.PROCESSING);
        settlement.setPayoutAttempts(settlement.getPayoutAttempts() + 1);
        settlement.setLastAttemptAt(OffsetDateTime.now());
        try {
            PaystackPaymentService.Transfer transfer = paystackPaymentService.initiateTransfer(
                    settlement.getPayoutReference(), settlement.getAmount(), settlement.getCurrency(),
                    settlement.getBeneficiary().getProviderRecipientCode(),
                    "LogiFlow supplier settlement " + settlement.getId());
            settlement.setProviderTransferCode(transfer.transferCode());
            settlement.setFailureReason(null);
            if ("success".equalsIgnoreCase(transfer.status())) {
                settlement.setStatus(SupplierSettlementStatus.SETTLED);
                settlement.setSettledAt(OffsetDateTime.now());
            } else if ("failed".equalsIgnoreCase(transfer.status())) {
                settlement.setStatus(SupplierSettlementStatus.FAILED);
                settlement.setFailureReason("Paystack reported a failed transfer");
            } else {
                settlement.setFailureReason("Waiting for Paystack transfer status: " + transfer.status());
            }
        } catch (PaystackPaymentService.ProviderOutcomeUnknownException ex) {
            settlement.setFailureReason("Paystack transfer outcome is unknown; reconciliation is required");
        } catch (ApiException ex) {
            settlement.setStatus(SupplierSettlementStatus.FAILED);
            settlement.setFailureReason(ex.getMessage());
        }
        settlementRepository.save(settlement);
    }

    private void validateBatchCandidate(SupplierSettlement settlement) {
        if (settlement.isDemo() || settlement.getStatus() != SupplierSettlementStatus.PENDING
                || settlement.getSettlementBatch() != null || settlement.getAmount() == null
                || settlement.getAmount().signum() <= 0) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Only non-demo, unbatched pending settlements can be selected");
        }
    }

    private SettlementBatch lockBatch(UUID batchId) {
        return batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Settlement batch not found"));
    }

    private void refreshBatchStatus(SettlementBatch batch) {
        if (batch == null) return;
        batch = lockBatch(batch.getId());
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
