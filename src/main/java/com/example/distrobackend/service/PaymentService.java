package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Order;
import com.example.distrobackend.Domain.entity.Payment;
import com.example.distrobackend.Domain.enums.OrderStatus;
import com.example.distrobackend.Domain.enums.PaymentStatus;
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
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private static final DateTimeFormatter DARAJA_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Africa/Nairobi"));

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final RestTemplate darajaRestTemplate;

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

    @Transactional(noRollbackFor = ApiException.class)
    public PaymentInitiateResponse initiatePayment(AuthenticatedUser actor, PaymentInitiateRequest request) {
        Order order = orderRepository.findByIdWithOwnership(request.orderId())
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
            markFailed(payment, ex.getMessage());
            throw ex;
        } catch (RuntimeException ex) {
            log.error("Daraja STK push failed for order {}", order.getId(), ex);
            markFailed(payment, "Payment provider unavailable");
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

        Payment payment = paymentRepository.findByMpesaCheckoutRequestIdForUpdate(callback.getCheckoutRequestId())
                .orElseThrow(() -> new ApiException(ErrorCode.PAYMENT_NOT_FOUND, "Payment not found"));
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
            if (payment.getOrder().getStatus() != OrderStatus.PENDING) {
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
        }
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
                .accountReference(order.getOrderNumber())
                .transactionDesc("Payment for Order " + order.getOrderNumber())
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
        Object resultCode = response.getBody() == null ? null : response.getBody().get("ResultCode");
        if (!response.getStatusCode().is2xxSuccessful() || resultCode == null
                || !String.valueOf(expectedResultCode).equals(String.valueOf(resultCode))) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "M-Pesa callback could not be reconciled");
        }
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

    private void markFailed(Payment payment, String reason) {
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
