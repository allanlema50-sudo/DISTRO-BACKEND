package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Order;
import com.example.distrobackend.Domain.entity.Payment;
import com.example.distrobackend.Domain.enums.OrderStatus;
import com.example.distrobackend.Domain.enums.PaymentStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.*;
import com.example.distrobackend.dto.mpesa.MpesaCallbackRequest;
import com.example.distrobackend.dto.mpesa.MpesaStkPushRequest;
import com.example.distrobackend.dto.mpesa.MpesaStkPushResponse;
import com.example.distrobackend.repository.OrderRepository;
import com.example.distrobackend.repository.PaymentRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;

    // Sandbox Credentials
    private static final String CONSUMER_KEY = "cpol7ARNvoMKIOoX9lPsXDFAAJ8Y2zc0WizUkoMWZuMPhqHs";
    private static final String CONSUMER_SECRET = "vw7nlCsEpaltj0vYoM8C0E0m0O4sfJzZfQseGjFhEIvL4DkdaoMYDuViSclZ3POX";
    private static final String SHORTCODE = "174379";
    private static final String PASSKEY = "bfb279f9aa9bdbcf158e97dd71a467cd2e0c893059b10f78e6b72ada1ed2c919";
    
    // IMPORTANT: Daraja cannot reach localhost. For real sandbox testing, you MUST use Ngrok or a public URL.
    // e.g. "https://<your-ngrok-id>.ngrok-free.app/api/v1/payments/mpesa/callback"
    private static final String CALLBACK_URL = "https://gullible-given-sherry.ngrok-free.dev/api/v1/payments/mpesa/callback"; 

    private static final String AUTH_URL = "https://sandbox.safaricom.co.ke/oauth/v1/generate?grant_type=client_credentials";
    private static final String STK_PUSH_URL = "https://sandbox.safaricom.co.ke/mpesa/stkpush/v1/processrequest";

    private final RestTemplate restTemplate = new RestTemplate();

    private String getDarajaAccessToken() {
        String auth = CONSUMER_KEY + ":" + CONSUMER_SECRET;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Basic " + encodedAuth);
        
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        ResponseEntity<Map> response = restTemplate.exchange(AUTH_URL, HttpMethod.GET, entity, Map.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            return String.valueOf(response.getBody().get("access_token"));
        }
        throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to authenticate with Daraja");
    }

    @Transactional
    public PaymentInitiateResponse initiatePayment(PaymentInitiateRequest request) {
        Order order = orderRepository.findById(request.orderId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Order not found"));

        paymentRepository.findByOrderId(order.getId()).ifPresent(payment -> {
            if (payment.getStatus() == PaymentStatus.CONFIRMED) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Order is already paid");
            }
        });

        java.math.BigDecimal totalAmount = order.getTotalAmount();
        if (totalAmount == null || totalAmount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid order amount");
        }

        // 1. Generate Timestamp and Password
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String passwordStr = SHORTCODE + PASSKEY + timestamp;
        String password = Base64.getEncoder().encodeToString(passwordStr.getBytes());

        // 2. Format Phone Number (Daraja strictly expects 2547XXXXXXXX)
        String phone = request.phoneNumber().replaceAll("[^0-9]", "");
        if (phone.startsWith("0")) phone = "254" + phone.substring(1);
        if (phone.startsWith("+")) phone = phone.substring(1);

        // 3. Build STK Push Request
        MpesaStkPushRequest stkRequest = MpesaStkPushRequest.builder()
                .businessShortCode(SHORTCODE)
                .password(password)
                .timestamp(timestamp)
                .transactionType("CustomerPayBillOnline")
                // Daraja sandbox can be flaky with large amounts. Hardcode to "1" if it fails, but trying real amount first:
                .amount(String.valueOf(totalAmount.intValue())) 
                .partyA(phone)
                .partyB(SHORTCODE)
                .phoneNumber(phone)
                .callBackURL(CALLBACK_URL)
                .accountReference(order.getOrderNumber())
                .transactionDesc("Payment for Order " + order.getOrderNumber())
                .build();

        // 4. Send Request to Daraja
        String accessToken = getDarajaAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + accessToken);
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<MpesaStkPushRequest> entity = new HttpEntity<>(stkRequest, headers);
        
        MpesaStkPushResponse mpesaResponse;
        try {
            ResponseEntity<MpesaStkPushResponse> response = restTemplate.postForEntity(STK_PUSH_URL, entity, MpesaStkPushResponse.class);
            mpesaResponse = response.getBody();
            if (mpesaResponse == null || mpesaResponse.getResponseCode() == null || !mpesaResponse.getResponseCode().equals("0")) {
                throw new ApiException(ErrorCode.INTERNAL_ERROR, "Daraja STK Push Failed: " + (mpesaResponse != null ? mpesaResponse.getCustomerMessage() : "Unknown"));
            }
        } catch (Exception e) {
            log.error("STK Push error: ", e);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, "Failed to initiate M-Pesa STK Push. Check Daraja credentials.");
        }

        // 5. Save the Payment to the database as PENDING
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setAmount(totalAmount);
        payment.setPhoneNumber(phone);
        payment.setMpesaCheckoutRequestId(mpesaResponse.getCheckoutRequestId());
        payment.setMpesaMerchantRequestId(mpesaResponse.getMerchantRequestId());
        payment.setInitiatedAt(OffsetDateTime.now());
        payment.setStatus(PaymentStatus.PENDING);
        
        payment = paymentRepository.save(payment);

        return new PaymentInitiateResponse(
                payment.getId(),
                mpesaResponse.getCheckoutRequestId(),
                PaymentStatus.PENDING,
                mpesaResponse.getCustomerMessage()
        );
    }

    @Transactional
    public void processMpesaCallback(MpesaCallbackRequest callbackRequest) {
        if (callbackRequest.getBody() == null || callbackRequest.getBody().getStkCallback() == null) {
            log.error("Invalid M-Pesa callback payload");
            return;
        }

        MpesaCallbackRequest.StkCallback stkCallback = callbackRequest.getBody().getStkCallback();
        String checkoutRequestId = stkCallback.getCheckoutRequestId();

        Payment payment = paymentRepository.findByMpesaCheckoutRequestId(checkoutRequestId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Payment not found for CheckoutRequestID: " + checkoutRequestId));

        if (stkCallback.getResultCode() != null && stkCallback.getResultCode() == 0) {
            // SUCCESS
            payment.setStatus(PaymentStatus.CONFIRMED);
            payment.setConfirmedAt(OffsetDateTime.now());
            
            // Extract Receipt Number
            if (stkCallback.getCallbackMetadata() != null && stkCallback.getCallbackMetadata().getItem() != null) {
                stkCallback.getCallbackMetadata().getItem().stream()
                        .filter(item -> "MpesaReceiptNumber".equals(item.getName()))
                        .findFirst()
                        .ifPresent(item -> payment.setMpesaReceiptNumber(String.valueOf(item.getValue())));
            }

            // Mark Order as CONFIRMED
            Order order = payment.getOrder();
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);

            log.info("Payment {} CONFIRMED. M-Pesa Receipt: {}", payment.getId(), payment.getMpesaReceiptNumber());

        } else {
            // FAILED (Insufficient balance, cancelled by user, timeout, etc.)
            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason(stkCallback.getResultDesc());
            log.warn("Payment {} FAILED. Reason: {}", payment.getId(), stkCallback.getResultDesc());
        }

        paymentRepository.save(payment);
    }

    public PaymentStatusResponse getPaymentStatus(UUID orderId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No payment found for this order"));

        return new PaymentStatusResponse(
                orderId,
                payment.getId(),
                payment.getStatus(),
                payment.getAmount(),
                payment.getMpesaReceiptNumber(),
                payment.getFailureReason()
        );
    }
}
