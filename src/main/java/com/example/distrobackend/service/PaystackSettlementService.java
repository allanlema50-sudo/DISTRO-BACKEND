package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.*;
import com.example.distrobackend.Domain.enums.*;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.*;
import com.example.distrobackend.repository.*;
import com.example.distrobackend.security.AuthenticatedUser;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
    private final SupplierPayoutAttemptRepository payoutAttemptRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final PaystackSettlementDispatchStore dispatchStore;
    private final PurchaseOrderShipmentRepository purchaseOrderShipmentRepository;

    @Value("${logiflow.settlements.delivery-hold-days:7}")
    private int deliveryHoldDays = 7;

    @Value("${paystack.beneficiary-review-required:false}")
    private boolean beneficiaryReviewRequired;

    @Value("${paystack.test-auto-settlement-enabled:false}")
    private boolean testAutoSettlementEnabled;

    @PostConstruct
    void validateDeliveryHoldConfiguration() {
        if (deliveryHoldDays < 0 || deliveryHoldDays > 365) {
            throw new IllegalStateException(
                    "logiflow.settlements.delivery-hold-days must be between 0 and 365");
        }
        if (testAutoSettlementEnabled && (!paystackPaymentService.enabled()
                || !paystackPaymentService.transfersEnabled()
                || !"TEST".equals(paystackPaymentService.mode()))) {
            throw new IllegalStateException("PAYSTACK_TEST_AUTO_SETTLEMENT_ENABLED=true requires "
                    + "PAYSTACK_ENABLED=true, PAYSTACK_TRANSFERS_ENABLED=true and PAYSTACK_MODE=test");
        }
    }

    @Transactional
    public PayoutBeneficiaryResponse createBeneficiary(AuthenticatedUser actor,
                                                       CreatePayoutBeneficiaryRequest request) {
        requireOrganizationAdmin(actor);
        if (request == null || !request.detailsConfirmed()) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Confirm the payout destination details before submitting");
        }
        PayoutMethod method = parseMethod(request.method());
        String currency = normalizeCurrency(request.currency());
        String accountName = requireText(request.accountName(), "accountName");
        String accountNumber;
        String bankCode = clean(request.bankCode());
        if (method == PayoutMethod.BANK_ACCOUNT) {
            accountNumber = requireDigits(request.accountNumber(), "accountNumber");
            PaystackPaymentService.DestinationOption bank = findDestinationOption(
                    paystackPaymentService.listBankDestinations(currency), bankCode);
            PaystackPaymentService.ResolvedBankAccount resolved = paystackPaymentService.resolveBankAccount(
                    accountNumber, bank.code(), currency);
            if (!sameAccountName(accountName, resolved.accountName())) {
                throw new ApiException(ErrorCode.CONFLICT,
                        "Confirmed account name does not match the name returned by Paystack; resolve the account again");
            }
            accountName = resolved.accountName();
            bankCode = bank.code();
        } else {
            accountNumber = normalizeMobile(request.mobileNumber());
            PaystackPaymentService.DestinationOption provider = findDestinationOption(
                    paystackPaymentService.listMobileMoneyDestinations(currency), bankCode);
            bankCode = provider.code();
        }
        Organization organization = organizationRepository.findByIdForUpdate(actor.organizationId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Organization not found"));
        if (!beneficiaryReviewRequired && beneficiaryRepository
                .findByOrganization_IdAndStatusAndCurrency(organization.getId(),
                        PayoutBeneficiaryStatus.ACTIVE, currency).isPresent()) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "The organization already has an active payout beneficiary for this currency");
        }
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
        beneficiary.setProviderMode(paystackPaymentService.mode());
        beneficiary.setCurrency(currency);
        beneficiary.setAccountName(accountName);
        beneficiary.setAccountMask(mask(accountNumber));
        beneficiary.setBankCode(bankCode);
        beneficiary.setProviderRecipientCode(recipient.recipientCode());
        beneficiary.setStatus(beneficiaryReviewRequired
                ? PayoutBeneficiaryStatus.PENDING_REVIEW : PayoutBeneficiaryStatus.ACTIVE);
        beneficiary.setCreatedBy(userRepository.getReferenceById(actor.userId()));
        beneficiary.setReviewNote(beneficiaryReviewRequired
                ? "Submitted for platform review"
                : "Provider destination created; organization administrator confirmed the details");
        return PayoutBeneficiaryResponse.from(beneficiaryRepository.save(beneficiary));
    }

    @Transactional(readOnly = true)
    public List<PayoutDestinationOptionResponse> listBankDestinations(AuthenticatedUser actor, String currency) {
        requireOrganizationAdmin(actor);
        String normalizedCurrency = normalizeCurrency(currency);
        return paystackPaymentService.listBankDestinations(normalizedCurrency).stream()
                .map(option -> new PayoutDestinationOptionResponse(option.name(), option.code(),
                        option.currency(), option.type(), option.active()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<PayoutDestinationOptionResponse> listMobileMoneyDestinations(
            AuthenticatedUser actor, String currency) {
        requireOrganizationAdmin(actor);
        String normalizedCurrency = normalizeCurrency(currency);
        return paystackPaymentService.listMobileMoneyDestinations(normalizedCurrency).stream()
                .map(option -> new PayoutDestinationOptionResponse(option.name(), option.code(),
                        option.currency(), option.type(), option.active()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ResolvedPayoutBankAccountResponse resolveBankAccount(
            AuthenticatedUser actor, ResolvePayoutBankAccountRequest request) {
        requireOrganizationAdmin(actor);
        String currency = normalizeCurrency(request.currency());
        String accountNumber = requireDigits(request.accountNumber(), "accountNumber");
        PaystackPaymentService.DestinationOption bank = findDestinationOption(
                paystackPaymentService.listBankDestinations(currency), request.bankCode());
        PaystackPaymentService.ResolvedBankAccount resolved = paystackPaymentService.resolveBankAccount(
                accountNumber, bank.code(), currency);
        return new ResolvedPayoutBankAccountResponse(resolved.accountName(), bank.code(), bank.name(),
                currency, mask(accountNumber));
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
            organizationRepository.findByIdForUpdate(beneficiary.getOrganization().getId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Organization not found"));
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
        List<SupplierSettlement> observedSettlements = settlementRepository.findAllById(ids);
        if (observedSettlements.size() != ids.size()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "Supplier settlement not found");
        }
        observedSettlements.stream().map(s -> s.getAllocation().getPayment().getId()).distinct().sorted()
                .forEach(paymentId -> checkoutPaymentRepository.findByIdForUpdate(paymentId)
                        .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout payment not found")));
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
            requireBeneficiaryMode(beneficiary, paystackPaymentService.mode());
            settlement.setBeneficiary(beneficiary);
            settlement.setProvider(PaymentProvider.PAYSTACK);
            settlement.setPayoutReference("lf_settlement_" + UUID.randomUUID().toString().replace("-", ""));
            total = total.add(settlement.getAmount());
        }
        SettlementBatch batch = new SettlementBatch();
        batch.setProvider(PaymentProvider.PAYSTACK);
        batch.setProviderMode(paystackPaymentService.mode());
        batch.setExecutionMode("MANUAL");
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

    public SettlementBatchResponse executeBatch(AuthenticatedUser actor, UUID batchId) {
        requirePlatformAdmin(actor);
        requireTransfersEnabled();
        List<UUID> settlementIds = dispatchStore.beginBatch(batchId, paystackPaymentService.mode());
        settlementIds.forEach(id -> dispatchAttempt(id, false));
        return dispatchStore.finishBatch(batchId);
    }

    /** Creates an auditable, one-settlement batch for the guarded TEST-only automation. */
    @Transactional
    public UUID createAutomaticTestBatchIfEligible(UUID settlementId) {
        requireTestAutomation();
        SupplierSettlement observed = settlementRepository.findById(settlementId).orElse(null);
        if (observed == null || observed.getSettlementBatch() != null) return null;
        UUID paymentId = observed.getAllocation().getPayment().getId();
        checkoutPaymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout payment not found"));
        SupplierSettlement settlement = settlementRepository.findByIdForUpdate(settlementId).orElse(null);
        if (settlement == null || settlement.getSettlementBatch() != null) return null;
        validateBatchCandidate(settlement);
        PayoutBeneficiary beneficiary = beneficiaryRepository
                .findByOrganization_IdAndStatusAndCurrency(settlement.getSupplierOrganization().getId(),
                        PayoutBeneficiaryStatus.ACTIVE, normalizeCurrency(settlement.getCurrency()))
                .orElse(null);
        if (beneficiary == null) return null;
        requireBeneficiaryMode(beneficiary, "TEST");

        SettlementBatch batch = new SettlementBatch();
        batch.setProvider(PaymentProvider.PAYSTACK);
        batch.setProviderMode("TEST");
        batch.setExecutionMode("TEST_AUTOMATIC");
        // The automated approval is explicit in executionMode; it is not attributed to a human.
        batch.setStatus(SettlementBatchStatus.APPROVED);
        batch.setApprovedAt(OffsetDateTime.now());
        batch.setCurrency(normalizeCurrency(settlement.getCurrency()));
        batch.setTotalAmount(settlement.getAmount());
        batch.setSettlementCount(1);
        batch.setNote("Automatically dispatched after delivery eligibility in Paystack TEST mode");
        batch = batchRepository.saveAndFlush(batch);

        settlement.setBeneficiary(beneficiary);
        settlement.setProvider(PaymentProvider.PAYSTACK);
        settlement.setSettlementBatch(batch);
        settlementRepository.saveAndFlush(settlement);
        return batch.getId();
    }

    /** Resumes a committed automatic batch after a restart, but never under LIVE credentials. */
    public SettlementBatchResponse executeAutomaticTestBatch(UUID batchId) {
        requireTestAutomation();
        requireTransfersEnabled();
        List<UUID> settlementIds = dispatchStore.beginBatch(batchId, "TEST");
        settlementIds.forEach(id -> dispatchAttempt(id, false));
        return dispatchStore.finishBatch(batchId);
    }

    public SupplierSettlementAdminResponse retrySettlement(AuthenticatedUser actor, UUID settlementId) {
        requirePlatformAdmin(actor);
        requireTransfersEnabled();
        dispatchAttempt(settlementId, true);
        return dispatchStore.getSettlement(settlementId);
    }

    @Transactional
    public SupplierSettlementAdminResponse reassignBeneficiary(
            AuthenticatedUser actor, UUID settlementId, ReassignSettlementBeneficiaryRequest request) {
        requirePlatformAdmin(actor);
        if (request == null || request.beneficiaryId() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "A replacement beneficiary is required");
        }
        SupplierSettlement observed = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Supplier settlement not found"));
        UUID paymentId = observed.getAllocation().getPayment().getId();
        checkoutPaymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout payment not found"));
        SupplierSettlement settlement = settlementRepository.findByIdForUpdate(settlementId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Supplier settlement not found"));
        if (settlement.getStatus() != SupplierSettlementStatus.FAILED
                && settlement.getStatus() != SupplierSettlementStatus.REVERSED) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Only failed or reversed settlements can be assigned a replacement beneficiary");
        }
        PayoutBeneficiary beneficiary = beneficiaryRepository.findByIdForUpdate(request.beneficiaryId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Payout beneficiary not found"));
        if (beneficiary.getStatus() != PayoutBeneficiaryStatus.ACTIVE
                || !beneficiary.getOrganization().getId().equals(settlement.getSupplierOrganization().getId())
                || !beneficiary.getCurrency().equalsIgnoreCase(settlement.getCurrency())) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Replacement beneficiary must be active and match the supplier and settlement currency");
        }
        settlement.setBeneficiary(beneficiary);
        return SupplierSettlementAdminResponse.from(settlementRepository.save(settlement));
    }

    /** Internal recovery for a batch execution interrupted before this pending item was dispatched. */
    public void resumePendingSettlement(UUID settlementId) {
        dispatchAttempt(settlementId, false);
    }

    private void dispatchAttempt(UUID settlementId, boolean retry) {
        PaystackSettlementDispatchStore.Attempt attempt = dispatchStore.prepareAttempt(
                settlementId, retry, paystackPaymentService.mode());
        if (attempt == null) return;
        try {
            PaystackPaymentService.Transfer transfer = paystackPaymentService.initiateTransfer(
                    attempt.reference(), attempt.amount(), attempt.currency(), attempt.recipientCode(),
                    "LogiFlow supplier settlement " + attempt.settlementId());
            dispatchStore.complete(attempt, transfer, null, false);
        } catch (PaystackPaymentService.ProviderOutcomeUnknownException ex) {
            dispatchStore.complete(attempt, null, null, true);
        } catch (ApiException ex) {
            dispatchStore.complete(attempt, null, ex.getMessage(), false);
        }
    }

    public void processPaystackWebhook(PaystackPaymentService.Webhook webhook) {
        dispatchStore.processPaystackWebhook(webhook);
    }

    @Transactional
    public Optional<PaystackSettlementDispatchStore.AttemptVerification> claimAttemptVerification(
            UUID attemptId, OffsetDateTime startedBefore, OffsetDateTime checkedBefore, OffsetDateTime checkedAt) {
        return dispatchStore.claimAttemptVerification(attemptId, startedBefore, checkedBefore, checkedAt);
    }

    @Transactional
    public void markPaystackTransferNotFound(String reference) {
        dispatchStore.markPaystackTransferNotFound(reference);
    }

    private void validateBatchCandidate(SupplierSettlement settlement) {
        if (settlement.isDemo() || settlement.getStatus() != SupplierSettlementStatus.PENDING
                || settlement.getSettlementBatch() != null || settlement.getAmount() == null
                || settlement.getAmount().signum() <= 0) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Only non-demo, unbatched pending settlements can be selected");
        }
        UUID paymentId = settlement.getAllocation().getPayment().getId();
        CheckoutPayment payment = checkoutPaymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout payment not found"));
        requireDeliveryHoldElapsed(settlement);
        boolean hasUnclearedDispute = disputeRepository.existsUnclearedForPaymentOrReference(
                paymentId, payment.getProviderReference());
        boolean hasOpenRefund = paymentRefundRepository.existsByPayment_IdAndStatusIn(paymentId,
                Set.of(RefundStatus.REQUESTED, RefundStatus.PENDING, RefundStatus.PROCESSING));
        if (hasUnclearedDispute || hasOpenRefund) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "This settlement is blocked by an open refund or unresolved/unreviewed payment dispute");
        }
    }

    private static void requireBeneficiaryMode(PayoutBeneficiary beneficiary, String expectedMode) {
        if (expectedMode == null || !expectedMode.equals(beneficiary.getProviderMode())) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "The active payout beneficiary was not created in the current Paystack mode; "
                            + "register and approve a beneficiary for this environment before payout");
        }
    }

    private void requireTestAutomation() {
        if (!testAutoSettlementEnabled || !paystackPaymentService.enabled()
                || !paystackPaymentService.transfersEnabled()
                || !"TEST".equals(paystackPaymentService.mode())) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED,
                    "Automatic settlement requires its explicit flag, enabled Paystack transfers, and TEST mode");
        }
    }

    private void requireDeliveryHoldElapsed(SupplierSettlement settlement) {
        UUID purchaseOrderId = settlement.getAllocation().getPurchaseOrder().getId();
        PurchaseOrderShipment shipment = purchaseOrderShipmentRepository
                .findByPurchaseOrderIdForUpdate(purchaseOrderId)
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                        "Supplier settlement is unavailable until its purchase order shipment is received"));
        if (shipment.getStatus() != PurchaseOrderShipmentStatus.RECEIVED || shipment.getReceivedAt() == null) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Supplier settlement is unavailable until the distributor confirms shipment receipt");
        }
        OffsetDateTime releaseAt = shipment.getReceivedAt().plusDays(deliveryHoldDays);
        if (OffsetDateTime.now().isBefore(releaseAt)) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Supplier settlement is held until the post-receipt return period ends at " + releaseAt);
        }
    }

    private SettlementBatch lockBatch(UUID batchId) {
        return batchRepository.findByIdForUpdate(batchId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Settlement batch not found"));
    }

    private void requireTransfersEnabled() {
        if (!paystackPaymentService.transfersEnabled()) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, "Paystack transfers are disabled");
        }
    }

    private static PayoutMethod parseMethod(String value) {
        try {
            return PayoutMethod.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "method must be MOBILE_MONEY or BANK_ACCOUNT");
        }
    }

    private static PaystackPaymentService.DestinationOption findDestinationOption(
            List<PaystackPaymentService.DestinationOption> options, String code) {
        String requestedCode = clean(code);
        return options.stream()
                .filter(option -> option.code().equalsIgnoreCase(requestedCode == null ? "" : requestedCode))
                .findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST,
                        "Select an active payout destination returned by Paystack"));
    }

    private static boolean sameAccountName(String submitted, String resolved) {
        return normalizeAccountName(submitted).equals(normalizeAccountName(resolved));
    }

    private static String normalizeAccountName(String value) {
        return value == null ? "" : value.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
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
