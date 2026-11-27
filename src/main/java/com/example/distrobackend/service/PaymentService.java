package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Order;
import com.example.distrobackend.Domain.entity.Payment;
import com.example.distrobackend.Domain.entity.PurchaseOrder;
import com.example.distrobackend.Domain.entity.PurchaseOrderSettlement;
import com.example.distrobackend.Domain.enums.OrderStatus;
import com.example.distrobackend.Domain.enums.PaymentMethod;
import com.example.distrobackend.Domain.enums.PaymentProvider;
import com.example.distrobackend.Domain.enums.PaymentStatus;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.PurchaseOrderStatus;
import com.example.distrobackend.Domain.enums.SettlementMethod;
import com.example.distrobackend.Domain.enums.SettlementPaymentStatus;
import com.example.distrobackend.Domain.enums.SettlementStatus;
import com.example.distrobackend.Domain.entity.PurchaseOrderSettlementPayment;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Util.PhoneNormalizer;
import com.example.distrobackend.dto.PaymentInitiateRequest;
import com.example.distrobackend.dto.PaymentInitiateResponse;
import com.example.distrobackend.dto.PaymentStatusResponse;
import com.example.distrobackend.dto.mpesa.MpesaCallbackRequest;
import com.example.distrobackend.dto.mpesa.MpesaStkPushResponse;
import com.example.distrobackend.repository.OrderRepository;
import com.example.distrobackend.repository.PaymentRepository;
import com.example.distrobackend.repository.PurchaseOrderRepository;
import com.example.distrobackend.repository.PurchaseOrderSettlementPaymentRepository;
import com.example.distrobackend.repository.PurchaseOrderSettlementRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PaymentService {

    private static final DateTimeFormatter DARAJA_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Africa/Nairobi"));

    private final PaymentRepository paymentRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderSettlementRepository settlementRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final RestTemplate darajaRestTemplate;
    private final FinancialLedgerService financialLedgerService;
    private final PurchaseOrderShipmentService purchaseOrderShipmentService;
    private final ApplicationEventPublisher eventPublisher;
    private final CustomerCollectionProvider collectionProvider;
    private final MpesaCallbackGuard callbackGuard;
    private final CustomerOrderPaystackPaymentService customerOrderPaystackPaymentService;
    private final PurchaseOrderSettlementPaymentRepository settlementPaymentRepository;
    private final PlatformFeeService platformFeeService;

    @Autowired
    public PaymentService(PaymentRepository paymentRepository,
                          PurchaseOrderRepository purchaseOrderRepository,
                          PurchaseOrderSettlementRepository settlementRepository,
                          OrderRepository orderRepository,
                          OrderService orderService,
                          RestTemplate darajaRestTemplate,
                          FinancialLedgerService financialLedgerService,
                          PurchaseOrderShipmentService purchaseOrderShipmentService,
                          ApplicationEventPublisher eventPublisher,
                          CustomerCollectionProvider collectionProvider,
                          MpesaCallbackGuard callbackGuard,
                          CustomerOrderPaystackPaymentService customerOrderPaystackPaymentService,
                          PurchaseOrderSettlementPaymentRepository settlementPaymentRepository,
                          PlatformFeeService platformFeeService) {
        this.paymentRepository = paymentRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.settlementRepository = settlementRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.darajaRestTemplate = darajaRestTemplate;
        this.financialLedgerService = financialLedgerService;
        this.purchaseOrderShipmentService = purchaseOrderShipmentService;
        this.eventPublisher = eventPublisher;
        this.collectionProvider = collectionProvider;
        this.callbackGuard = callbackGuard;
        this.customerOrderPaystackPaymentService = customerOrderPaystackPaymentService;
        this.settlementPaymentRepository = settlementPaymentRepository;
        this.platformFeeService = platformFeeService;
    }

    /** Compatibility constructor for existing customer-order unit fixtures. */
    public PaymentService(PaymentRepository paymentRepository,
                          OrderRepository orderRepository,
                          OrderService orderService,
                          RestTemplate darajaRestTemplate) {
        this(paymentRepository, null, null, orderRepository, orderService, darajaRestTemplate,
                null, null, null, null, null, null, null, null);
    }

    @Value("${mpesa.base-url}")
    private String baseUrl;
    @Value("${mpesa.consumer-key}")
    private String consumerKey;
    @Value("${mpesa.consumer-secret}")
    private String consumerSecret;
    @Value("${mpesa.shortcode}")
    private String shortcode;
    @Value("${mpesa.passkey}")
    private String passkey;
    @Value("${mpesa.callback-url}")
    private String callbackUrl;
    @Value("${mpesa.callback-secret:}")
    private String callbackSecret;
    @Value("${mpesa.verify-callback:true}")
    private boolean verifyCallback;
    @Value("${mpesa.final-failure-result-codes:1032}")
    private String finalFailureResultCodes;

    /**
     * Presentation switch. Customer checkout is marked paid without Daraja,
     * and admin can dispatch the delivery. Turn off after the demo.
     */
    @Value("${app.demo.customer-fulfillment.enabled:false}")
    private boolean demoCustomerFulfillment;
    private Set<Integer> terminalFailureResultCodeSet = Set.of();

    @PostConstruct
    void initializeFinalFailureResultCodes() {
        if (isBlank(finalFailureResultCodes)) {
            terminalFailureResultCodeSet = Set.of();
            return;
        }

        try {
            terminalFailureResultCodeSet = Arrays.stream(finalFailureResultCodes.split(",", -1))
                    .map(String::trim)
                    .filter(token -> !token.isEmpty())
                    .map(this::parseFinalFailureResultCode)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException(
                    "MPESA_FINAL_FAILURE_RESULT_CODES contains an invalid result code", ex);
        }
    }

    @Transactional(noRollbackFor = ApiException.class)
    public PaymentInitiateResponse initiatePayment(AuthenticatedUser actor, PaymentInitiateRequest request) {
        if ("PURCHASE_ORDER".equalsIgnoreCase(request.orderType())) {
            if (request.provider() != null && !"MPESA".equalsIgnoreCase(request.provider())) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Unsupported payment provider");
            }
            return initiatePurchaseOrderPayment(actor, request);
        }
        if (request.orderType() != null && !"CUSTOMER_ORDER".equalsIgnoreCase(request.orderType())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Unsupported payment orderType");
        }
        String requestedProvider = request.provider() == null || request.provider().isBlank()
                ? "DARAJA" : request.provider().trim().toUpperCase(java.util.Locale.ROOT);
        if ("PAYSTACK".equals(requestedProvider) || "PAYSTACK_CARD".equals(requestedProvider)) {
            if (customerOrderPaystackPaymentService == null) {
                throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, "Paystack card checkout is not configured");
            }
            return customerOrderPaystackPaymentService.initiate(actor, request);
        }
        if (!"DARAJA".equals(requestedProvider) && !"MPESA".equals(requestedProvider)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "provider must be DARAJA, MPESA or PAYSTACK");
        }
        Order order = orderRepository.findByIdWithOwnershipForUpdate(request.orderId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));
        requireOrderAccess(actor, order);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Only pending orders can be paid");
        }
        if (demoCustomerFulfillment) {
            return confirmDemoCustomerPayment(order, request.phoneNumber(), request.idempotencyKey(), false);
        }
        ensureConfigured();

        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            Payment keyedPayment = paymentRepository.findByIdempotencyKey(request.idempotencyKey().trim()).orElse(null);
            if (keyedPayment != null && !keyedPayment.getOrder().getId().equals(order.getId())) {
                throw new ApiException(ErrorCode.CONFLICT, "Idempotency key has already been used");
            }
        }

        Payment payment = paymentRepository.findByOrderIdForUpdate(order.getId()).orElse(null);
        if (payment != null) {
            if (payment.getStatus() == PaymentStatus.CONFIRMED
                    || payment.getStatus() == PaymentStatus.RECONCILED) {
                throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "Order is already paid");
            }
            if (payment.getStatus() == PaymentStatus.INITIATING) {
                throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                        "A payment request is already being initiated");
            }
            if (payment.getStatus() == PaymentStatus.PENDING) {
                if (payment.getProvider() == PaymentProvider.DARAJA
                        && !isBlank(payment.getMpesaCheckoutRequestId())) {
                    return response(payment, "Payment request is already pending");
                }
                throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                        "Another payment provider has an unresolved payment for this order");
            }
        } else {
            payment = new Payment();
            payment.setOrder(order);
            payment.setAmount(order.getTotalAmount());
        }

        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            payment.setIdempotencyKey(request.idempotencyKey().trim());
        } else if (payment.getProvider() == PaymentProvider.PAYSTACK) {
            payment.setIdempotencyKey(null);
        }

        String phone = PhoneNormalizer.normalize(request.phoneNumber());
        BigDecimal amount = order.getTotalAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid order amount");
        }
        int integerAmount;
        try {
            integerAmount = amount.setScale(0, RoundingMode.UNNECESSARY).intValueExact();
        } catch (ArithmeticException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "M-Pesa amount must be a whole positive KES amount");
        }
        if (integerAmount <= 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "M-Pesa amount must be positive");
        }

        payment.setStatus(PaymentStatus.INITIATING);
        payment.setProvider(PaymentProvider.DARAJA);
        payment.setMethod(PaymentMethod.MPESA);
        payment.setProviderReference(null);
        payment.setProviderAuthorizationUrl(null);
        payment.setProviderMode(null);
        payment.setPhoneNumber(phone);
        payment.setInitiatedAt(OffsetDateTime.now());
        payment.setFailureReason(null);
        payment.setMpesaCheckoutRequestId(null);
        payment.setMpesaMerchantRequestId(null);
        paymentRepository.saveAndFlush(payment);

        try {
            MpesaStkPushResponse mpesaResponse = sendStkPush(order, phone, integerAmount);
            if (mpesaResponse == null || !"0".equals(mpesaResponse.getResponseCode())
                    || isBlank(mpesaResponse.getCheckoutRequestId())
                    || mpesaResponse.getCheckoutRequestId().length() > 100
                    || isBlank(mpesaResponse.getMerchantRequestId())
                    || mpesaResponse.getMerchantRequestId().length() > 100) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR,
                        "Daraja returned an incomplete payment response");
            }
            payment.setMpesaCheckoutRequestId(mpesaResponse.getCheckoutRequestId());
            payment.setMpesaMerchantRequestId(mpesaResponse.getMerchantRequestId());
            payment.setStatus(PaymentStatus.PENDING);
            paymentRepository.save(payment);
            return response(payment, mpesaResponse.getCustomerMessage());
        } catch (ApiException ex) {
            markInitiationFailed(payment, ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Daraja STK push failed for order {}", order.getId(), ex);
            markInitiationFailed(payment, "Payment provider unavailable");
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Payment provider unavailable");
        }
    }

    @Transactional
    public void processMpesaCallback(String suppliedCallbackSecret, MpesaCallbackRequest callbackRequest) {
        processMpesaCallback(suppliedCallbackSecret, null, null, callbackRequest);
    }

    @Transactional
    public void processMpesaCallback(String suppliedCallbackSecret, String callbackToken, String remoteAddress,
                                     MpesaCallbackRequest callbackRequest) {
        if (callbackGuard != null) {
            callbackGuard.verifyCustomerCallback(suppliedCallbackSecret, callbackToken, remoteAddress);
        } else {
            verifyCallbackSecret(suppliedCallbackSecret);
        }
        if (callbackRequest == null || callbackRequest.getBody() == null
                || callbackRequest.getBody().getStkCallback() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid M-Pesa callback payload");
        }

        MpesaCallbackRequest.StkCallback callback = callbackRequest.getBody().getStkCallback();
        if (callback.getCheckoutRequestId() == null || callback.getCheckoutRequestId().isBlank()
                || callback.getResultCode() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Incomplete M-Pesa callback payload");
        }

        PurchaseOrderSettlement purchaseSettlement = settlementRepository == null ? null
                : settlementRepository.findByMpesaCheckoutRequestId(callback.getCheckoutRequestId()).orElse(null);
        if (purchaseSettlement != null) {
            processPurchaseOrderCallback(callback, purchaseSettlement);
            return;
        }

        Object[] paymentAndOrderIds = paymentRepository
                .findPaymentAndOrderIdsByMpesaCheckoutRequestId(callback.getCheckoutRequestId())
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found"));
        UUID paymentId = (UUID) paymentAndOrderIds[0];
        UUID orderId = (UUID) paymentAndOrderIds[1];
        Order lockedOrder = orderRepository.findByIdWithOwnershipForUpdate(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found"));
        if (!callback.getCheckoutRequestId().equals(payment.getMpesaCheckoutRequestId())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "M-Pesa callback correlation failed");
        }
        if (isBlank(payment.getMpesaMerchantRequestId())
                || !payment.getMpesaMerchantRequestId().equals(callback.getMerchantRequestId())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "M-Pesa callback correlation failed");
        }

        if (payment.getStatus() == PaymentStatus.CONFIRMED
                || payment.getStatus() == PaymentStatus.RECONCILED
                || payment.getStatus() == PaymentStatus.REVERSED) {
            return; // Idempotent replay handling.
        }

        ensureCallbackVerificationEnabled();
        verifyCallbackResult(callback.getCheckoutRequestId(), callback.getResultCode());
        payment.setCallbackRawPayload(rawPayload(callback));

        if (callback.getResultCode() == 0) {
            String receipt = callbackValue(callback, "MpesaReceiptNumber");
            String callbackAmount = callbackValue(callback, "Amount");
            if (receipt == null || receipt.isBlank()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Successful callback has no receipt");
            }
            if (!receipt.matches("[A-Za-z0-9]{1,50}")) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid M-Pesa receipt");
            }
            if (callbackAmount == null || callbackAmount.isBlank()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Successful callback has no amount");
            }
            try {
                if (new BigDecimal(callbackAmount).compareTo(payment.getAmount()) != 0) {
                    throw new ApiException(ErrorCode.BAD_REQUEST, "M-Pesa amount does not match the order");
                }
            } catch (NumberFormatException ex) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid M-Pesa amount");
            }
            if (lockedOrder.getStatus() != OrderStatus.PENDING) {
                throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                        "Payment cannot confirm an order in its current state");
            }
            payment.setMpesaReceiptNumber(receipt);
            payment.setStatus(PaymentStatus.CONFIRMED);
            payment.setConfirmedAt(OffsetDateTime.now());
            paymentRepository.save(payment);
            orderService.confirmPaidOrder(payment.getOrder().getId());
            if (financialLedgerService != null) {
                financialLedgerService.recordCustomerOrderCapture(
                        payment.getId(), payment.getOrder().getOrganization(),
                        payment.getAmount(), payment.getCurrency());
            }
        } else {
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(callback.getResultDesc());
            paymentRepository.save(payment);
            orderService.failPaymentAndReleaseOrder(
                    payment.getOrder().getId(),
                    callback.getResultDesc() == null ? "M-Pesa payment failed" : callback.getResultDesc());
        }
    }

    /**
     * Reconciles an expired order whose accepted STK request has not produced
     * a callback yet. The order lock is acquired before the payment lock, just
     * like initiation and callback processing.
     *
     * @return true when an in-flight payment was handled; false when normal
     *         reservation expiry should proceed
     */
    @Transactional
    public boolean reconcileExpiredPayment(UUID orderId) {
        Order order = orderRepository.findByIdWithOwnershipForUpdate(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));
        if (order.getStatus() != OrderStatus.PENDING) {
            return false;
        }

        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId).orElse(null);
        if (payment != null && payment.getProvider() == PaymentProvider.PAYSTACK
                && (payment.getStatus() == PaymentStatus.INITIATING
                || payment.getStatus() == PaymentStatus.PENDING)) {
            return customerOrderPaystackPaymentService != null
                    && customerOrderPaystackPaymentService.hasUnresolvedAttemptForPayment(payment.getId());
        }
        if (payment == null || payment.getStatus() != PaymentStatus.PENDING
                || isBlank(payment.getMpesaCheckoutRequestId())) {
            return false;
        }

        MpesaQueryResult result = queryMpesaPayment(payment.getMpesaCheckoutRequestId());
        if (result.resultCode() == 0) {
            OffsetDateTime reconciledAt = OffsetDateTime.now();
            payment.setStatus(PaymentStatus.RECONCILED);
            payment.setConfirmedAt(reconciledAt);
            payment.setFailureReason(null);
            payment.setReconciledAt(reconciledAt);
            payment.setReconciledNote("Reconciled by Daraja STK query after callback deadline");
            payment.setCallbackRawPayload(Map.of(
                    "source", "DARAJA_STK_QUERY",
                    "checkoutRequestId", payment.getMpesaCheckoutRequestId(),
                    "resultCode", result.resultCode(),
                    "resultDescription", result.resultDescription() == null
                            ? "" : result.resultDescription()));
            paymentRepository.save(payment);
            orderService.confirmPaidOrder(orderId);
            if (financialLedgerService != null) {
                financialLedgerService.recordCustomerOrderCapture(
                        payment.getId(), order.getOrganization(),
                        payment.getAmount(), payment.getCurrency());
            }
        } else if (isConfiguredFinalFailureCode(result.resultCode())) {
            String reason = isBlank(result.resultDescription())
                    ? "M-Pesa payment did not complete"
                    : result.resultDescription();
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(reason);
            paymentRepository.save(payment);
            orderService.failPaymentAndReleaseOrder(orderId, reason);
        } else {
            // A nonzero query result is not automatically a final payment
            // outcome. Keep the payment and reservation pending until a
            // verified callback or an explicitly configured terminal code is
            // received.
            throw new ApiException(ErrorCode.INTERNAL_ERROR,
                    "M-Pesa payment status is not final; reconciliation will be retried");
        }
        return true;
    }

    /**
     * Payment-ledger consistency check: a confirmed payment becomes RECONCILED
     * when Daraja's STK query still reports success and the captured journal
     * equals the payment amount. No M-Pesa statement is read.
     */
    @Transactional
    public boolean checkLedgerConsistency(UUID paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId).orElse(null);
        if (payment == null || payment.getStatus() != PaymentStatus.CONFIRMED
                || financialLedgerService == null || isBlank(payment.getMpesaCheckoutRequestId())) {
            return false;
        }
        java.util.Optional<BigDecimal> captured = financialLedgerService.capturedGross(
                "CUSTOMER_ORDER_PAYMENT", payment.getId());
        if (captured.isEmpty() || captured.get() == null
                || captured.get().compareTo(payment.getAmount()) != 0) {
            return false;
        }
        MpesaQueryResult result = queryMpesaPayment(payment.getMpesaCheckoutRequestId());
        if (result.resultCode() != 0) {
            return false;
        }
        payment.setStatus(PaymentStatus.RECONCILED);
        payment.setReconciledAt(OffsetDateTime.now());
        payment.setReconciledNote("Payment-ledger consistency check: STK query success and captured journal match");
        paymentRepository.save(payment);
        return true;
    }

    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(AuthenticatedUser actor, UUID orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND, "No payment found for this order"));
        requireOrderAccess(actor, payment.getOrder());
        return new PaymentStatusResponse(orderId, payment.getId(), payment.getStatus(), payment.getAmount(),
                payment.getMpesaReceiptNumber(), payment.getFailureReason(),
                payment.getProvider() == null ? "DARAJA" : payment.getProvider().name(),
                payment.getProviderReference(), payment.getProviderAuthorizationUrl(), payment.getProviderMode());
    }

    private MpesaStkPushResponse sendStkPush(Order order, String phone, int amount) {
        return sendStkPush(order.getOrderNumber(), "Payment for Order " + order.getOrderNumber(), phone, amount);
    }

    private MpesaStkPushResponse sendStkPush(String accountReference, String description, String phone, int amount) {
        if (collectionProvider == null) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED);
        }
        String registeredCallback = callbackGuard == null ? callbackUrl : callbackGuard.callbackUrlWithToken(callbackUrl);
        return collectionProvider.push(accountReference, description, phone, amount, registeredCallback);
    }

    private PaymentInitiateResponse initiatePurchaseOrderPayment(AuthenticatedUser actor,
                                                                  PaymentInitiateRequest request) {
        if (actor == null || actor.organizationId() == null || actor.role() == null
                || actor.role().organizationtype() != Organizationtype.DISTRIBUTOR) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "A distributor identity is required to pay a purchase order");
        }
        PurchaseOrder order = purchaseOrderRepository.findByIdForPayment(request.orderId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Purchase order not found"));
        if (!actor.organizationId().equals(order.getBuyerOrganization().getId())) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        if (order.getCheckout() != null) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Purchase orders in a marketplace checkout must be paid through the checkout payment");
        }
        if (order.getStatus() != PurchaseOrderStatus.APPROVED) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Only approved purchase orders can be paid");
        }
        java.util.List<PurchaseOrderSettlement> priorSettlements =
                settlementRepository.findByPurchaseOrderIdOrderByCreatedAtDesc(order.getId());
        if (FulfilmentGate.paidInFull(order, priorSettlements)) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "Purchase order is already paid");
        }
        if (settlementPaymentRepository != null
                && settlementPaymentRepository.existsBySettlement_PurchaseOrder_Id(order.getId())) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "A bank transfer payment is already under way for this purchase order");
        }
        ensureConfigured();
        String key = request.idempotencyKey() == null ? null : request.idempotencyKey().trim();
        if (!isBlank(key)) {
            PurchaseOrderSettlement existing = settlementRepository.findByIdempotencyKey(key).orElse(null);
            if (existing != null) {
                if (!existing.getPurchaseOrder().getId().equals(order.getId())) {
                    throw new ApiException(ErrorCode.CONFLICT, "Idempotency key has already been used");
                }
                if (existing.getStatus() == SettlementStatus.PENDING
                        && !isBlank(existing.getMpesaCheckoutRequestId())) {
                    return new PaymentInitiateResponse(existing.getId(), existing.getMpesaCheckoutRequestId(),
                            PaymentStatus.PENDING, "Payment request is already pending",
                            existing.getMpesaCheckoutRequestId(), "MPESA");
                }
                if (existing.getStatus() == SettlementStatus.SETTLED) {
                    throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "Purchase order is already paid");
                }
            }
        }
        BigDecimal amount = order.getTotalAmount();
        int integerAmount;
        try {
            integerAmount = amount.setScale(0, RoundingMode.UNNECESSARY).intValueExact();
        } catch (ArithmeticException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "M-Pesa amount must be a whole positive KES amount");
        }
        if (amount.signum() <= 0 || integerAmount <= 0 || !"KES".equalsIgnoreCase(order.getCurrency())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Purchase order must have a positive KES total");
        }
        String phone = PhoneNormalizer.normalize(request.phoneNumber());
        PurchaseOrderSettlement settlement = new PurchaseOrderSettlement();
        settlement.setPurchaseOrder(order);
        settlement.setMethod(SettlementMethod.MPESA);
        settlement.setAmount(amount);
        settlement.setTotalDue(amount);
        settlement.setAmountPaid(BigDecimal.ZERO.setScale(2));
        settlement.setCurrency("KES");
        settlement.setStatus(SettlementStatus.PENDING);
        settlement.setIdempotencyKey(isBlank(key) ? null : key);
        settlement.setPhoneNumber(phone);
        settlementRepository.saveAndFlush(settlement);
        try {
            MpesaStkPushResponse response = sendStkPush(order.getPurchaseOrderNumber(),
                    "Payment for " + order.getPurchaseOrderNumber(), phone, integerAmount);
            if (response == null || !"0".equals(response.getResponseCode())
                    || isBlank(response.getCheckoutRequestId()) || isBlank(response.getMerchantRequestId())) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR, "Daraja returned an incomplete payment response");
            }
            settlement.setMpesaCheckoutRequestId(response.getCheckoutRequestId());
            settlement.setMpesaMerchantRequestId(response.getMerchantRequestId());
            settlement.setProviderReference(response.getCheckoutRequestId());
            settlementRepository.save(settlement);
            return new PaymentInitiateResponse(settlement.getId(), response.getCheckoutRequestId(),
                    PaymentStatus.PENDING, response.getCustomerMessage(), response.getCheckoutRequestId(), "MPESA");
        } catch (ApiException ex) {
            settlement.setStatus(SettlementStatus.FAILED);
            settlement.setFailureReason(ex.getMessage());
            settlementRepository.save(settlement);
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Daraja STK push failed for purchase order {}", order.getId(), ex);
            settlement.setStatus(SettlementStatus.FAILED);
            settlement.setFailureReason("Payment provider unavailable");
            settlementRepository.save(settlement);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Payment provider unavailable");
        }
    }

    private void processPurchaseOrderCallback(MpesaCallbackRequest.StkCallback callback,
                                               PurchaseOrderSettlement candidate) {
        PurchaseOrderSettlement settlement = settlementRepository.findByIdForUpdate(candidate.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found"));
        if (!callback.getCheckoutRequestId().equals(settlement.getMpesaCheckoutRequestId())
                || isBlank(settlement.getMpesaMerchantRequestId())
                || !settlement.getMpesaMerchantRequestId().equals(callback.getMerchantRequestId())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "M-Pesa callback correlation failed");
        }
        if (settlement.getStatus() == SettlementStatus.SETTLED || settlement.getStatus() == SettlementStatus.REVERSED) {
            return;
        }
        ensureCallbackVerificationEnabled();
        verifyCallbackResult(callback.getCheckoutRequestId(), callback.getResultCode());
        settlement.setCallbackRawPayload(rawPayload(callback));
        if (callback.getResultCode() == 0) {
            String receipt = callbackValue(callback, "MpesaReceiptNumber");
            String amount = callbackValue(callback, "Amount");
            if (isBlank(receipt) || !receipt.matches("[A-Za-z0-9]{1,50}") || isBlank(amount)) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Successful callback is missing valid receipt details");
            }
            try {
                if (new BigDecimal(amount).compareTo(settlement.getAmount()) != 0) {
                    throw new ApiException(ErrorCode.BAD_REQUEST, "M-Pesa amount does not match the purchase order");
                }
            } catch (NumberFormatException ex) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid M-Pesa amount");
            }
            settlement.setStatus(SettlementStatus.SETTLED);
            settlement.setProviderReference(receipt);
            settlement.setSettledAt(OffsetDateTime.now());
            finalizePurchaseOrderSettlement(settlement);
        } else {
            settlement.setStatus(SettlementStatus.FAILED);
            settlement.setFailureReason(callback.getResultDesc());
        }
        settlementRepository.save(settlement);
        if (settlement.getStatus() == SettlementStatus.SETTLED && settlement.getPurchaseOrder() != null) {
            if (eventPublisher != null) {
                eventPublisher.publishEvent(PurchaseOrderPaymentVerifiedEvent.purchaseOrder(
                        settlement.getPurchaseOrder().getId()));
            } else if (purchaseOrderShipmentService != null) {
                purchaseOrderShipmentService.handleSettledPurchaseOrder(settlement.getPurchaseOrder().getId());
            }
        }
    }

    /**
     * A direct purchase order paid by STK push is collected on the platform till,
     * so the full amount is owed to the manufacturer (no commission) and the
     * payment is recorded as one confirmed M-Pesa payment on the settlement.
     * The platform fee, which defaults to zero, accrues here as for any method.
     */
    private void finalizePurchaseOrderSettlement(PurchaseOrderSettlement settlement) {
        PurchaseOrder order = settlement.getPurchaseOrder();
        settlement.setMethod(SettlementMethod.MPESA);
        settlement.setAmountPaid(settlement.getAmount());
        if (settlement.getTotalDue() == null) {
            settlement.setTotalDue(settlement.getAmount());
        }
        String receipt = settlement.getProviderReference();
        if (settlementPaymentRepository != null && order != null && receipt != null
                && settlementPaymentRepository.findBySupplierOrganization_IdAndPayerReference(
                        order.getSupplierOrganization().getId(), receipt).isEmpty()) {
            PurchaseOrderSettlementPayment payment = new PurchaseOrderSettlementPayment();
            payment.setSettlement(settlement);
            payment.setSupplierOrganization(order.getSupplierOrganization());
            payment.setMethod(SettlementMethod.MPESA);
            payment.setPayerReference(receipt);
            payment.setAmount(settlement.getAmount());
            payment.setStatus(SettlementPaymentStatus.CONFIRMED);
            payment.setConfirmedAt(OffsetDateTime.now());
            settlementPaymentRepository.save(payment);
        }
        if (financialLedgerService != null && order != null) {
            financialLedgerService.recordFullAmountCapture(FinancialLedgerService.PURCHASE_ORDER_SETTLEMENT,
                    settlement.getId(), order.getSupplierOrganization(), settlement.getAmount(),
                    settlement.getCurrency());
        }
        if (platformFeeService != null) {
            platformFeeService.accrueOnSettlement(settlement);
        }
    }

    private String getDarajaAccessToken() {
        String basic = Base64.getEncoder().encodeToString(
                (consumerKey + ":" + consumerSecret).getBytes(StandardCharsets.UTF_8));
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + basic);
        ResponseEntity<Map<String, Object>> response = darajaRestTemplate.exchange(
                baseUrl + "/oauth/v1/generate?grant_type=client_credentials",
                HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {});
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null
                || response.getBody().get("access_token") == null) {
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Daraja authentication failed");
        }
        return String.valueOf(response.getBody().get("access_token"));
    }

    private void verifyCallbackResult(String checkoutRequestId, int expectedResultCode) {
        MpesaQueryResult result = queryMpesaPayment(checkoutRequestId);
        if (result.resultCode() != expectedResultCode) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "M-Pesa callback could not be reconciled");
        }
    }

    private MpesaQueryResult queryMpesaPayment(String checkoutRequestId) {
        ensureConfigured();
        String timestamp = DARAJA_TIMESTAMP.format(java.time.Instant.now());
        String password = Base64.getEncoder().encodeToString(
                (shortcode + passkey + timestamp).getBytes(StandardCharsets.UTF_8));
        Map<String, String> request = Map.of(
                "BusinessShortCode", shortcode,
                "Password", password,
                "Timestamp", timestamp,
                "CheckoutRequestID", checkoutRequestId);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(getDarajaAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map<String, Object>> response = darajaRestTemplate.exchange(
                baseUrl + "/mpesa/stkpushquery/v1/query",
                HttpMethod.POST, new HttpEntity<>(request, headers),
                new ParameterizedTypeReference<>() {});
        Map<String, Object> body = response.getBody();
        Object resultCode = body == null ? null : body.get("ResultCode");
        if (!response.getStatusCode().is2xxSuccessful() || resultCode == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "M-Pesa payment status could not be reconciled");
        }
        try {
            return new MpesaQueryResult(Integer.parseInt(String.valueOf(resultCode)),
                    body.get("ResultDesc") == null ? null : String.valueOf(body.get("ResultDesc")));
        } catch (NumberFormatException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "M-Pesa returned an invalid payment status");
        }
    }

    private record MpesaQueryResult(int resultCode, String resultDescription) {}

    private boolean isConfiguredFinalFailureCode(int resultCode) {
        return terminalFailureResultCodeSet.contains(resultCode);
    }

    private int parseFinalFailureResultCode(String token) {
        int resultCode = Integer.parseInt(token);
        if (resultCode < 0) {
            throw new NumberFormatException("Result codes cannot be negative");
        }
        return resultCode;
    }

    private void ensureCallbackVerificationEnabled() {
        if (!verifyCallback) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED,
                    "M-Pesa callback verification must remain enabled");
        }
    }

    private void verifyCallbackSecret(String suppliedCallbackSecret) {
        if (isBlank(callbackSecret)) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED,
                    "M-Pesa callback secret is not configured");
        }
        if (isBlank(suppliedCallbackSecret)
                || !MessageDigest.isEqual(
                        callbackSecret.getBytes(StandardCharsets.UTF_8),
                        suppliedCallbackSecret.getBytes(StandardCharsets.UTF_8))) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid M-Pesa callback credentials");
        }
    }

    private void requireOrderAccess(AuthenticatedUser actor, Order order) {
        if (actor == null || order == null) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        boolean customerOwns = actor.userId().equals(order.getCustomer().getId());
        boolean organizationOwns = actor.organizationId() != null && order.getOrganization() != null
                && actor.organizationId().equals(order.getOrganization().getId());
        if (!customerOwns && !organizationOwns) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void ensureConfigured() {
        if (isBlank(baseUrl) || isBlank(consumerKey) || isBlank(consumerSecret)
                || isBlank(shortcode) || isBlank(passkey) || isBlank(callbackUrl)
                || isBlank(callbackSecret)) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED);
        }
    }

    /**
     * An initiation failure is not a final payment outcome. Keep the order
     * pending so the customer can retry; the reservation expiry job remains
     * responsible for abandoned orders.
     */
    private void markInitiationFailed(Payment payment, String reason) {
        payment.setStatus(PaymentStatus.FAILED);
        payment.setFailureReason(reason);
        paymentRepository.save(payment);
    }

    /**
     * Marks a still-pending customer order paid without calling M-Pesa.
     * No-op when the order is already past payment. Used by demo dispatch.
     */
    @Transactional
    public void confirmPendingDemoPayment(UUID orderId) {
        if (!demoCustomerFulfillment) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Demo customer fulfillment is turned off");
        }
        Order order = orderRepository.findByIdWithOwnershipForUpdate(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));
        if (order.getStatus() != OrderStatus.PENDING) {
            return;
        }
        String phone = order.getCustomer() == null ? null : order.getCustomer().getPhoneNumber();
        confirmDemoCustomerPayment(order, phone, null, true);
    }

    private PaymentInitiateResponse confirmDemoCustomerPayment(Order order, String rawPhone,
                                                               String idempotencyKey, boolean lenientPhone) {
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Only pending orders can be paid");
        }
        if (order.getTotalAmount() == null || order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid order amount");
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            Payment keyedPayment = paymentRepository.findByIdempotencyKey(idempotencyKey.trim()).orElse(null);
            if (keyedPayment != null && keyedPayment.getOrder() != null
                    && !keyedPayment.getOrder().getId().equals(order.getId())) {
                throw new ApiException(ErrorCode.CONFLICT, "Idempotency key has already been used");
            }
        }
        String phone = lenientPhone ? fallbackPhone(rawPhone) : PhoneNormalizer.normalize(rawPhone);
        Payment payment = paymentRepository.findByOrderIdForUpdate(order.getId()).orElse(null);
        if (payment != null && (payment.getStatus() == PaymentStatus.CONFIRMED
                || payment.getStatus() == PaymentStatus.RECONCILED)) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "Order is already paid");
        }
        if (payment == null) {
            payment = new Payment();
            payment.setOrder(order);
            payment.setAmount(order.getTotalAmount());
        }
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            payment.setIdempotencyKey(idempotencyKey.trim());
        }
        payment.setMethod(PaymentMethod.OTHER);
        payment.setProvider(PaymentProvider.DEMO);
        payment.setStatus(PaymentStatus.CONFIRMED);
        payment.setPhoneNumber(phone);
        payment.setAmount(order.getTotalAmount());
        payment.setInitiatedAt(OffsetDateTime.now());
        payment.setConfirmedAt(OffsetDateTime.now());
        payment.setFailureReason(null);
        payment.setMpesaCheckoutRequestId(null);
        payment.setMpesaMerchantRequestId(null);
        payment.setMpesaReceiptNumber(("DEMO" + UUID.randomUUID().toString().replace("-", "")).substring(0, 16));
        payment.setReconciledNote("DEMO — no money transferred");
        paymentRepository.save(payment);
        orderService.confirmPaidOrder(order.getId());
        return response(payment, "Demo payment confirmed. No money was transferred.");
    }

    private static String fallbackPhone(String rawPhone) {
        if (rawPhone == null || rawPhone.isBlank()) {
            return "254700000000";
        }
        try {
            return PhoneNormalizer.normalize(rawPhone);
        } catch (RuntimeException ex) {
            return "254700000000";
        }
    }

    private PaymentInitiateResponse response(Payment payment, String message) {
        return new PaymentInitiateResponse(payment.getId(), payment.getMpesaCheckoutRequestId(),
                payment.getStatus(), message);
    }

    private String callbackValue(MpesaCallbackRequest.StkCallback callback, String name) {
        if (callback.getCallbackMetadata() == null || callback.getCallbackMetadata().getItem() == null) {
            return null;
        }
        return callback.getCallbackMetadata().getItem().stream()
                .filter(item -> name.equals(item.getName()) && item.getValue() != null)
                .map(item -> String.valueOf(item.getValue()))
                .findFirst().orElse(null);
    }

    private Map<String, Object> rawPayload(MpesaCallbackRequest.StkCallback callback) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("merchantRequestId", callback.getMerchantRequestId());
        payload.put("checkoutRequestId", callback.getCheckoutRequestId());
        payload.put("resultCode", callback.getResultCode());
        payload.put("resultDesc", callback.getResultDesc());
        return payload;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
