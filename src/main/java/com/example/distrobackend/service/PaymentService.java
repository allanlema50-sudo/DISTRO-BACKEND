package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Order;
import com.example.distrobackend.Domain.entity.Payment;
import com.example.distrobackend.Domain.entity.PurchaseOrder;
import com.example.distrobackend.Domain.entity.PurchaseOrderSettlement;
import com.example.distrobackend.Domain.enums.OrderStatus;
import com.example.distrobackend.Domain.enums.PaymentStatus;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.PurchaseOrderStatus;
import com.example.distrobackend.Domain.enums.SettlementStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Util.PhoneNormalizer;
import com.example.distrobackend.dto.PaymentInitiateRequest;
import com.example.distrobackend.dto.PaymentInitiateResponse;
import com.example.distrobackend.dto.PaymentStatusResponse;
import com.example.distrobackend.dto.mpesa.MpesaCallbackRequest;
import com.example.distrobackend.dto.mpesa.MpesaStkPushRequest;
import com.example.distrobackend.dto.mpesa.MpesaStkPushResponse;
import com.example.distrobackend.repository.OrderRepository;
import com.example.distrobackend.repository.PaymentRepository;
import com.example.distrobackend.repository.PurchaseOrderRepository;
import com.example.distrobackend.repository.PurchaseOrderSettlementRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    public PaymentService(PaymentRepository paymentRepository,
                          PurchaseOrderRepository purchaseOrderRepository,
                          PurchaseOrderSettlementRepository settlementRepository,
                          OrderRepository orderRepository,
                          OrderService orderService,
                          RestTemplate darajaRestTemplate) {
        this.paymentRepository = paymentRepository;
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.settlementRepository = settlementRepository;
        this.orderRepository = orderRepository;
        this.orderService = orderService;
        this.darajaRestTemplate = darajaRestTemplate;
    }

    /** Compatibility constructor for existing customer-order unit fixtures. */
    public PaymentService(PaymentRepository paymentRepository,
                          OrderRepository orderRepository,
                          OrderService orderService,
                          RestTemplate darajaRestTemplate) {
        this(paymentRepository, null, null, orderRepository, orderService, darajaRestTemplate);
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
        Order order = orderRepository.findByIdWithOwnershipForUpdate(request.orderId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));
        requireOrderAccess(actor, order);
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Only pending orders can be paid");
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
            if (payment.getStatus() == PaymentStatus.PENDING
                    && payment.getMpesaCheckoutRequestId() != null) {
                return response(payment, "Payment request is already pending");
            }
        } else {
            payment = new Payment();
            payment.setOrder(order);
            payment.setAmount(order.getTotalAmount());
        }

        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            payment.setIdempotencyKey(request.idempotencyKey().trim());
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
        verifyCallbackSecret(suppliedCallbackSecret);
        if (callbackRequest == null || callbackRequest.getBody() == null
                || callbackRequest.getBody().getStkCallback() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid M-Pesa callback payload");
        }

        MpesaCallbackRequest.StkCallback callback = callbackRequest.getBody().getStkCallback();
        if (callback.getCheckoutRequestId() == null || callback.getCheckoutRequestId().isBlank()
                || callback.getResultCode() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Incomplete M-Pesa callback payload");
        }

        PurchaseOrderSettlement purchaseSettlement = settlementRepository
                .findByMpesaCheckoutRequestId(callback.getCheckoutRequestId()).orElse(null);
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

    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(AuthenticatedUser actor, UUID orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND, "No payment found for this order"));
        requireOrderAccess(actor, payment.getOrder());
        return new PaymentStatusResponse(orderId, payment.getId(), payment.getStatus(), payment.getAmount(),
                payment.getMpesaReceiptNumber(), payment.getFailureReason());
    }

    private MpesaStkPushResponse sendStkPush(Order order, String phone, int amount) {
        return sendStkPush(order.getOrderNumber(), "Payment for Order " + order.getOrderNumber(), phone, amount);
    }

    private MpesaStkPushResponse sendStkPush(String accountReference, String description, String phone, int amount) {
        String timestamp = DARAJA_TIMESTAMP.format(java.time.Instant.now());
        String passwordText = shortcode + passkey + timestamp;
        String password = Base64.getEncoder().encodeToString(passwordText.getBytes(StandardCharsets.UTF_8));
        MpesaStkPushRequest request = MpesaStkPushRequest.builder()
                .businessShortCode(shortcode)
                .password(password)
                .timestamp(timestamp)
                .transactionType("CustomerPayBillOnline")
                .amount(String.valueOf(amount))
                .partyA(phone.replace("+", ""))
                .partyB(shortcode)
                .phoneNumber(phone.replace("+", ""))
                .callBackURL(callbackUrl)
                .accountReference(accountReference)
                .transactionDesc(description)
                .build();

        String accessToken = getDarajaAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<MpesaStkPushResponse> response = darajaRestTemplate.postForEntity(
                baseUrl + "/mpesa/stkpush/v1/processrequest",
                new HttpEntity<>(request, headers), MpesaStkPushResponse.class);
        return response.getBody();
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
        if (order.getStatus() != PurchaseOrderStatus.APPROVED) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "Only approved purchase orders can be paid");
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
        settlement.setAmount(amount);
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
        } else {
            settlement.setStatus(SettlementStatus.FAILED);
            settlement.setFailureReason(callback.getResultDesc());
        }
        settlementRepository.save(settlement);
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
