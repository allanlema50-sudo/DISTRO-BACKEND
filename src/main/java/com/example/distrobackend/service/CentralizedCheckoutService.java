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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;
import java.net.URI;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.*;
import java.nio.charset.StandardCharsets;
import java.time.format.DateTimeFormatter;
import java.time.ZoneId;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class CentralizedCheckoutService {
    private static final String DEMO_LABEL = "DEMO \u2014 NO MONEY TRANSFERRED";
    private final CheckoutRepository checkoutRepository;
    private final CheckoutPaymentRepository checkoutPaymentRepository;
    private final CheckoutItemRepository checkoutItemRepository;
    private final PaymentAllocationRepository allocationRepository;
    private final SupplierSettlementRepository supplierSettlementRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final StockItemRepository stockItemRepository;
    private final WarehouseRepository warehouseRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final PurchaseOrderService purchaseOrderService;
    private final CheckoutPaymentAttemptStore paymentAttemptStore;
    private final FinancialLedgerService financialLedgerService;
    private final PurchaseOrderShipmentService purchaseOrderShipmentService;
    private final ApplicationEventPublisher eventPublisher;
    private final PaystackPaymentService paystackPaymentService;
    @Qualifier("darajaRestTemplate") private final RestTemplate darajaRestTemplate;

    @Value("${logiflow.marketplace-checkout.enabled:false}") private boolean marketplaceCheckoutEnabled;
    @Value("${logiflow.demo-payments.enabled:false}") private boolean demoEnabled;
    @Value("${logiflow.demo-payments.allowed-organization-ids:}") private String demoOrganizationIds;
    @Value("${logiflow.demo-payments.outcome:SUCCESS}") private String demoOutcome;
    @Value("${logiflow.demo-payments.confirmation-delay-seconds:3}") private long confirmationDelaySeconds;
    @Value("${mpesa.base-url:https://sandbox.safaricom.co.ke}") private String mpesaBaseUrl;
    @Value("${mpesa.auth-url}") private String mpesaAuthUrl;
    @Value("${mpesa.stk-push-url}") private String mpesaStkPushUrl;
    @Value("${mpesa.stk-query-url}") private String mpesaStkQueryUrl;
    @Value("${mpesa.consumer-key:}") private String mpesaConsumerKey;
    @Value("${mpesa.consumer-secret:}") private String mpesaConsumerSecret;
    @Value("${mpesa.shortcode:}") private String mpesaShortcode;
    @Value("${mpesa.passkey:}") private String mpesaPasskey;
    @Value("${mpesa.checkout-callback-url:}") private String mpesaCheckoutCallbackUrl;
    @Value("${mpesa.callback-secret:}") private String mpesaCallbackSecret;

    @PostConstruct
    void validateDemoConfiguration() {
        if (demoEnabled) {
            allowedDemoOrganizationIds();
        }
        if (!Set.of("SUCCESS", "FAILED", "CANCELLED", "TIMED_OUT").contains(demoOutcome.toUpperCase(Locale.ROOT))) {
            throw new IllegalStateException("logiflow.demo-payments.outcome must be SUCCESS, FAILED, CANCELLED, or TIMED_OUT");
        }
        if (confirmationDelaySeconds < 0 || confirmationDelaySeconds > 300) {
            throw new IllegalStateException("DEMO payment confirmation delay must be between 0 and 300 seconds");
        }
    }

    @Transactional
    public CheckoutResponse create(AuthenticatedUser actor, CreateCheckoutRequest request, String idempotencyKey) {
        requireDistributor(actor);
        if (!marketplaceCheckoutEnabled) throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, "Marketplace checkout is disabled");
        String key = clean(idempotencyKey);
        if (key == null) throw new ApiException(ErrorCode.BAD_REQUEST, "Idempotency-Key header is required");
        if (key != null) {
            Checkout existing = checkoutRepository.findByBuyerOrganization_IdAndIdempotencyKey(actor.organizationId(), key).orElse(null);
            if (existing != null) return response(existing);
        }
        if (request.supplierOrders().isEmpty()) throw new ApiException(ErrorCode.BAD_REQUEST, "Checkout requires at least one supplier order");
        Set<UUID> suppliers = new HashSet<>();
        for (var supplierOrder : request.supplierOrders()) {
            if (!suppliers.add(supplierOrder.supplierOrganizationId())) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Use one supplier order group per supplier");
            }
            warehouseRepository.findByIdAndOrganization_IdAndActiveTrue(supplierOrder.destinationWarehouseId(), actor.organizationId())
                    .orElseThrow(() -> new ApiException(ErrorCode.BAD_REQUEST, "Destination warehouse must be active and owned by the distributor"));
        }

        Organization buyer = organizationRepository.findById(actor.organizationId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Buyer organization not found"));
        BigDecimal total = BigDecimal.ZERO;
        List<PurchaseOrder> orders = new ArrayList<>();
        for (var supplierOrder : request.supplierOrders()) {
            List<PurchaseOrderItemRequest> lines = supplierOrder.items().stream()
                    .map(item -> new PurchaseOrderItemRequest(item.stockItemId(), item.quantity(), item.distributorOfferId())).toList();
            PurchaseOrderResponse poResponse = purchaseOrderService.create(actor,
                    new CreatePurchaseOrderRequest(supplierOrder.supplierOrganizationId(), lines));
            PurchaseOrder order = purchaseOrderRepository.findByIdWithItems(poResponse.id())
                    .orElseThrow(() -> new ApiException(ErrorCode.INTERNAL_ERROR, "Created purchase order could not be loaded"));
            total = total.add(order.getTotalAmount());
            orders.add(order);
        }
        Checkout checkout = new Checkout();
        checkout.setCheckoutReference("CHK-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase(Locale.ROOT));
        checkout.setIdempotencyKey(key);
        checkout.setBuyerOrganization(buyer);
        checkout.setBuyerType("DISTRIBUTOR_ORGANIZATION");
        checkout.setCreatedBy(userRepository.getReferenceById(actor.userId()));
        checkout.setStatus(CheckoutStatus.PAYMENT_PENDING);
        checkout.setTotalAmount(total);
        checkout.setCurrency("KES");
        checkout = checkoutRepository.saveAndFlush(checkout);

        for (int i = 0; i < orders.size(); i++) {
            PurchaseOrder order = orders.get(i);
            var supplierRequest = request.supplierOrders().get(i);
            order.setCheckout(checkout);
            order.setDestinationWarehouse(warehouseRepository.findByIdAndOrganization_IdAndActiveTrue(
                    supplierRequest.destinationWarehouseId(), actor.organizationId()).orElseThrow());
            purchaseOrderRepository.save(order);
            Warehouse warehouse = warehouseRepository.findByIdAndOrganization_IdAndActiveTrue(
                    supplierRequest.destinationWarehouseId(), actor.organizationId()).orElseThrow();
            for (PurchaseOrderItem line : order.getItems()) {
                CheckoutItem item = new CheckoutItem();
                item.setCheckout(checkout);
                item.setPurchaseOrder(order);
                item.setSupplierOrganization(order.getSupplierOrganization());
                item.setProductId(line.getStockItem().getId());
                item.setSupplierNameSnapshot(safeName(order.getSupplierOrganization().getName()));
                item.setProductNameSnapshot(line.getStockItem().getName());
                item.setQuantity(line.getQuantity());
                item.setUnitPrice(line.getUnitPrice());
                item.setLineTotal(line.getUnitPrice().multiply(BigDecimal.valueOf(line.getQuantity())));
                item.setCurrency(order.getCurrency());
                item.setDestinationWarehouse(warehouse);
                checkoutItemRepository.save(item);
            }
        }
        return response(checkout);
    }

    @Transactional(readOnly = true)
    public Page<CheckoutResponse> history(AuthenticatedUser actor, Pageable pageable) {
        requireDistributor(actor);
        return checkoutRepository.findByBuyerOrganization_IdOrderByCreatedAtDesc(actor.organizationId(), pageable).map(this::response);
    }

    @Transactional(readOnly = true)
    public CheckoutResponse get(AuthenticatedUser actor, UUID checkoutId) {
        requireDistributor(actor);
        return response(checkoutRepository.findByIdAndBuyerOrganization_Id(checkoutId, actor.organizationId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout not found")));
    }

    @Transactional
    public CheckoutResponse initiatePayment(AuthenticatedUser actor, UUID checkoutId, InitiateCheckoutPaymentRequest request,
                                            String idempotencyHeader) {
        requireDistributor(actor);
        Checkout checkout = checkoutRepository.findByIdAndBuyerOrganization_Id(checkoutId, actor.organizationId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout not found"));
        String method = request.method().trim().toUpperCase(Locale.ROOT);
        // Older/current distributor clients distinguish demo payment choices
        // by channel. They all represent a simulated payment and must retain
        // the same organization allowlist and non-production restrictions.
        boolean demo = Set.of("DEMO", "DEMO_MPESA", "DEMO_CARD").contains(method);
        if (demo) ensureDemoAuthorized(actor);
        boolean paystack = method.equals("PAYSTACK") || method.startsWith("PAYSTACK_");
        String paystackChannel = paystackChannel(method, request.channel());
        PaymentMethod paymentMethod = switch (method) {
            case "DEMO", "DEMO_MPESA", "DEMO_CARD" -> PaymentMethod.OTHER;
            case "MPESA_SANDBOX", "MPESA" -> PaymentMethod.MPESA;
            case "PAYSTACK", "PAYSTACK_CARD", "PAYSTACK_MPESA" -> paystackMethod(paystackChannel);
            case "PAYSTACK_PESALINK", "PAYSTACK_BANK_TRANSFER" -> PaymentMethod.OTHER;
            default -> throw new ApiException(ErrorCode.BAD_REQUEST,
                    "method must be MPESA_SANDBOX, MPESA, PAYSTACK, PAYSTACK_CARD, PAYSTACK_MPESA, PAYSTACK_PESALINK, PAYSTACK_BANK_TRANSFER, DEMO, DEMO_MPESA, or DEMO_CARD");
        };
        int mpesaAmount = (!demo && !paystack) ? requireWholeKes(checkout.getTotalAmount(), checkout.getCurrency()) : 0;
        String idempotencyKey = clean(idempotencyHeader) != null ? clean(idempotencyHeader) : clean(request.idempotencyKey());
        CheckoutPaymentAttemptStore.StartResult attempt = paymentAttemptStore.start(checkoutId,
                actor.organizationId(), paymentMethod, checkout.getTotalAmount(), checkout.getCurrency(),
                demo, demo ? DEMO_LABEL : null, idempotencyKey);
        if (!attempt.created()) {
            CheckoutPayment existing = checkoutPaymentRepository.findById(attempt.paymentId())
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout payment not found"));
            checkout.setPayment(existing);
            return response(checkout);
        }
        if (paystack) {
            String reference = "LF-" + checkout.getCheckoutReference() + "-"
                    + attempt.paymentId().toString().replace("-", "").substring(0, 12);
            paymentAttemptStore.preparePaystack(attempt.paymentId(), reference);
            try {
                PaystackPaymentService.Initialization result = paystackPaymentService.initialize(
                        reference, checkout.getTotalAmount(), checkout.getCurrency(),
                        checkout.getCreatedBy().getEmail(), paystackChannel,
                        attempt.paymentId().toString(), checkout.getCheckoutReference());
                paymentAttemptStore.markPaystackAccepted(attempt.paymentId(), result.providerReference(),
                        result.authorizationUrl(), result.accessCode(), result.channel());
            } catch (PaystackPaymentService.ProviderOutcomeUnknownException ex) {
                paymentAttemptStore.markOutcomeUnknown(attempt.paymentId());
                throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED,
                        "Paystack payment status is temporarily unknown; retry by checking the checkout status");
            } catch (RuntimeException ex) {
                paymentAttemptStore.markFailed(attempt.paymentId(),
                        ex instanceof ApiException ? ex.getMessage() : "Paystack request failed");
                throw ex;
            }
        } else if (!demo) {
            DarajaStkResult result;
            try {
                result = initiateDaraja(attempt.paymentId(), checkout.getCheckoutReference(),
                        request.phoneNumber(), mpesaAmount);
            } catch (StkPushOutcomeUnknownException ex) {
                paymentAttemptStore.markOutcomeUnknown(attempt.paymentId());
                result = null;
            } catch (RuntimeException ex) {
                paymentAttemptStore.markFailed(attempt.paymentId(),
                        ex instanceof ApiException ? ex.getMessage() : "Daraja request failed before acceptance could be confirmed");
                throw ex;
            }
            if (result != null) {
                // If this DB write fails after Daraja accepted the request, the
                // durable PENDING attempt and callback paymentId still allow
                // callback reconciliation without a second STK push.
                paymentAttemptStore.markAccepted(attempt.paymentId(), result.checkoutRequestId(),
                        result.merchantRequestId(), result.responseDescription());
            }
        }
        CheckoutPayment payment = checkoutPaymentRepository.findById(attempt.paymentId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout payment not found"));
        checkout.setPayment(payment);
        return response(checkout);
    }

    private DarajaStkResult initiateDaraja(UUID paymentId, String checkoutReference, String phone, int amount) {
        if (phone == null || phone.isBlank()) throw new ApiException(ErrorCode.BAD_REQUEST, "phoneNumber is required for M-Pesa STK Push");
        validateDarajaConfiguration();
        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBasicAuth(mpesaConsumerKey, mpesaConsumerSecret);
        ResponseEntity<Map<String, Object>> tokenResult = darajaRestTemplate.exchange(
                mpesaAuthUrl, HttpMethod.GET, new HttpEntity<>(authHeaders),
                new ParameterizedTypeReference<>() {});
        Object accessToken = tokenResult.getBody() == null ? null : tokenResult.getBody().get("access_token");
        if (accessToken == null) throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, "Daraja authentication failed");
        String timestamp = java.time.LocalDateTime.now(ZoneId.of("Africa/Nairobi")).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String password = Base64.getEncoder().encodeToString((mpesaShortcode + mpesaPasskey + timestamp).getBytes(StandardCharsets.UTF_8));
        String callbackUrl = org.springframework.web.util.UriComponentsBuilder.fromUriString(mpesaCheckoutCallbackUrl)
                .queryParam("paymentId", paymentId).build().encode().toUriString();
        Map<String,Object> body = Map.ofEntries(
                Map.entry("BusinessShortCode", mpesaShortcode), Map.entry("Password", password),
                Map.entry("Timestamp", timestamp), Map.entry("TransactionType", "CustomerPayBillOnline"),
                Map.entry("Amount", amount), Map.entry("PartyA", phone), Map.entry("PartyB", mpesaShortcode),
                Map.entry("PhoneNumber", phone), Map.entry("CallBackURL", callbackUrl),
                Map.entry("AccountReference", checkoutReference),
                Map.entry("TransactionDesc", "LogiFlow checkout"));
        HttpHeaders headers = new HttpHeaders(); headers.setBearerAuth(String.valueOf(accessToken)); headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map<String, Object>> result;
        try {
            result = darajaRestTemplate.exchange(
                    mpesaStkPushUrl, HttpMethod.POST, new HttpEntity<>(body, headers),
                    new ParameterizedTypeReference<>() {});
        } catch (org.springframework.web.client.RestClientException ex) {
            throw new StkPushOutcomeUnknownException(ex);
        }
        Map<String, Object> data=result.getBody();
        if (data == null || !"0".equals(String.valueOf(data.get("ResponseCode")))) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, "Daraja did not accept the STK request");
        }
        if (data.get("CheckoutRequestID") == null || data.get("MerchantRequestID") == null) {
            throw new StkPushOutcomeUnknownException(null);
        }
        return new DarajaStkResult(String.valueOf(data.get("CheckoutRequestID")),
                String.valueOf(data.get("MerchantRequestID")),
                String.valueOf(data.getOrDefault("ResponseDescription", "Accepted")));
    }

    private record DarajaStkResult(String checkoutRequestId, String merchantRequestId, String responseDescription) {}

    private static PaymentMethod paystackMethod(String channel) {
        return switch (channel) {
            case "mobile_money" -> PaymentMethod.MPESA;
            case "card" -> PaymentMethod.CARD;
            default -> PaymentMethod.OTHER;
        };
    }

    private static String paystackChannel(String method, String requestedChannel) {
        if (method.equals("PAYSTACK_CARD")) return "card";
        if (method.equals("PAYSTACK_MPESA")) return "mobile_money";
        if (method.equals("PAYSTACK_PESALINK") || method.equals("PAYSTACK_BANK_TRANSFER")) return "bank_transfer";
        return requestedChannel == null || requestedChannel.isBlank()
                ? "card" : requestedChannel.trim().toLowerCase(Locale.ROOT);
    }

    private static final class StkPushOutcomeUnknownException extends RuntimeException {
        private StkPushOutcomeUnknownException(Throwable cause) { super(cause); }
    }

    private static int requireWholeKes(BigDecimal amount, String currency) {
        if (!"KES".equalsIgnoreCase(currency) || amount == null || amount.signum() <= 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "M-Pesa checkout total must be a positive KES amount");
        }
        try {
            return amount.setScale(0, java.math.RoundingMode.UNNECESSARY).intValueExact();
        } catch (ArithmeticException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "M-Pesa checkout total must be a whole KES amount");
        }
    }

    private void validateDarajaConfiguration() {
        String expectedHost = switch (mpesaBaseUrl) {
            case "https://sandbox.safaricom.co.ke" -> "sandbox.safaricom.co.ke";
            case "https://api.safaricom.co.ke" -> "api.safaricom.co.ke";
            default -> null;
        };
        if (expectedHost == null || mpesaConsumerKey.isBlank() || mpesaConsumerSecret.isBlank()
                || mpesaShortcode.isBlank() || mpesaPasskey.isBlank()) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, "M-Pesa is not configured for a supported Daraja environment");
        }
        requireDarajaEndpoint(mpesaAuthUrl, expectedHost, "MPESA_AUTH_URL");
        requireDarajaEndpoint(mpesaStkPushUrl, expectedHost, "MPESA_STK_PUSH_URL");
        requireDarajaEndpoint(mpesaStkQueryUrl, expectedHost, "MPESA_STK_QUERY_URL");
        try {
            URI callback = URI.create(mpesaCheckoutCallbackUrl);
            if (!"https".equalsIgnoreCase(callback.getScheme()) || callback.getHost() == null
                    || callback.getUserInfo() != null || callback.getFragment() != null
                    || !"/api/v1/checkouts/mpesa/callback".equals(callback.getPath())) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED,
                    "Set MPESA_CHECKOUT_CALLBACK_URL to the public HTTPS /api/v1/checkouts/mpesa/callback endpoint");
        }
    }

    private static void requireDarajaEndpoint(String value, String expectedHost, String propertyName) {
        try {
            URI endpoint = URI.create(value);
            if (!"https".equalsIgnoreCase(endpoint.getScheme()) || !expectedHost.equalsIgnoreCase(endpoint.getHost())
                    || endpoint.getUserInfo() != null || endpoint.getFragment() != null
                    || (endpoint.getPort() != -1 && endpoint.getPort() != 443)) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException ex) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED,
                    propertyName + " must use the selected Daraja environment host " + expectedHost);
        }
    }

    
    @Transactional
    public CheckoutResponse cancel(AuthenticatedUser actor, UUID checkoutId) {
        requireDistributor(actor);
        Checkout checkout = checkoutRepository.findByIdAndBuyerOrganization_Id(checkoutId, actor.organizationId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout not found"));
        if (checkout.getStatus() != CheckoutStatus.PAYMENT_PENDING)
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "Only an unpaid checkout can be cancelled");
        CheckoutPayment payment = checkoutPaymentRepository.findFirstByCheckout_IdOrderByInitiatedAtDesc(checkoutId).orElse(null);
        if (payment != null) checkout.setPayment(payment);
        if (payment != null && payment.getStatus() == CheckoutPaymentStatus.SUCCESS)
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT, "Checkout payment is no longer pending");
        if (payment != null && payment.getStatus() == CheckoutPaymentStatus.PENDING && !payment.isDemo()) {
            throw new ApiException(ErrorCode.PAYMENT_STATE_CONFLICT,
                    "An M-Pesa request may already be processing; wait for provider confirmation before cancelling");
        }
        if (payment != null && payment.getStatus() == CheckoutPaymentStatus.PENDING) {
            payment.setStatus(CheckoutPaymentStatus.CANCELLED);
            payment.setConfirmedAt(OffsetDateTime.now());
            payment.setFailureReason("Cancelled by buyer");
            checkoutPaymentRepository.save(payment);
        }
        cancelAndRelease(checkout);
        checkout.setStatus(CheckoutStatus.CANCELLED);
        checkoutRepository.save(checkout);
        return response(checkout);
    }

    @Transactional
    public void processPaystackWebhook(byte[] rawBody, String signature, String legacySignature) {
        processPaystackWebhook(paystackPaymentService.parseAndVerifyWebhook(rawBody, signature, legacySignature));
    }

    @Transactional
    public void processPaystackWebhook(PaystackPaymentService.Webhook webhook) {
        if (!Set.of("charge.success", "charge.failed").contains(webhook.event())) return;

        CheckoutPayment payment = checkoutPaymentRepository.findByProviderReferenceForUpdate(webhook.reference())
                .orElse(null);
        // Paystack may retry an event after an older deployment has already
        // handled it. A reference that is not ours is acknowledged without
        // revealing whether a payment exists.
        if (payment == null || payment.getProvider() != com.example.distrobackend.Domain.enums.PaymentProvider.PAYSTACK) return;
        if (payment.getStatus() != CheckoutPaymentStatus.PENDING) return;

        if ("charge.success".equals(webhook.event())) {
            if (!"success".equalsIgnoreCase(webhook.status())) {
                payment.setStatus(CheckoutPaymentStatus.FAILED);
                payment.setFailureReason("Paystack success event had a non-success transaction status");
                payment.setConfirmedAt(OffsetDateTime.now());
                payment.getCheckout().setStatus(CheckoutStatus.PAYMENT_FAILED);
                checkoutPaymentRepository.save(payment);
                checkoutRepository.save(payment.getCheckout());
                return;
            }
            long expectedMinor = paystackMinorAmount(payment.getAmount(), payment.getCurrency());
            if (webhook.amountMinor() == null || webhook.amountMinor() != expectedMinor
                    || webhook.currency() == null || !payment.getCurrency().equalsIgnoreCase(webhook.currency())) {
                payment.setStatus(CheckoutPaymentStatus.FAILED);
                payment.setFailureReason("Paystack success amount or currency did not match the checkout");
                payment.setConfirmedAt(OffsetDateTime.now());
                payment.getCheckout().setStatus(CheckoutStatus.PAYMENT_FAILED);
                checkoutPaymentRepository.save(payment);
                checkoutRepository.save(payment.getCheckout());
                return;
            }
            payment.setStatus(CheckoutPaymentStatus.SUCCESS);
            payment.setConfirmedAt(OffsetDateTime.now());
            payment.setFailureReason(null);
            payment.getCheckout().setStatus(CheckoutStatus.PAID);
            recordAllocations(payment);
            publishPaymentVerified(payment);
        } else {
            payment.setStatus(CheckoutPaymentStatus.FAILED);
            payment.setFailureReason(webhook.gatewayResponse() == null
                    ? "Paystack reported a failed charge" : webhook.gatewayResponse());
            payment.setConfirmedAt(OffsetDateTime.now());
            payment.getCheckout().setStatus(CheckoutStatus.PAYMENT_FAILED);
        }
        checkoutPaymentRepository.save(payment);
        checkoutRepository.save(payment.getCheckout());
    }

    private static long paystackMinorAmount(BigDecimal amount, String currency) {
        if (amount == null || currency == null || !"KES".equalsIgnoreCase(currency)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Paystack checkout currency must be KES");
        }
        try {
            return amount.movePointRight(2).setScale(0, java.math.RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Paystack checkout amount must have at most two decimals");
        }
    }

    @Transactional
    public void processMpesaCallback(UUID paymentId, String suppliedSecret,
                                     com.example.distrobackend.dto.mpesa.MpesaCallbackRequest request) {
        // Daraja callbacks do not provide a configurable custom header. If a
        // shared-secret header is supplied, validate it; otherwise authenticate
        // the provider request and outcome through Daraja's STK query API.
        if (suppliedSecret != null && !suppliedSecret.isBlank()
                && (mpesaCallbackSecret.isBlank() || !java.security.MessageDigest.isEqual(
                mpesaCallbackSecret.getBytes(StandardCharsets.UTF_8), suppliedSecret.getBytes(StandardCharsets.UTF_8)))) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid M-Pesa callback credentials");
        }
        var callback = request == null || request.getBody() == null ? null : request.getBody().getStkCallback();
        if (paymentId == null || callback == null || callback.getCheckoutRequestId() == null
                || callback.getMerchantRequestId() == null || callback.getResultCode() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Invalid M-Pesa checkout callback");
        }
        CheckoutPayment payment = checkoutPaymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout payment not found"));
        if (payment.getStatus() != CheckoutPaymentStatus.PENDING) return;
        if (payment.getMpesaCheckoutRequestId() != null
                && !payment.getMpesaCheckoutRequestId().equals(callback.getCheckoutRequestId())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "M-Pesa checkout request correlation failed");
        }
        if (payment.getMpesaMerchantRequestId() != null
                && !payment.getMpesaMerchantRequestId().equals(callback.getMerchantRequestId())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "M-Pesa merchant request correlation failed");
        }
        validateDarajaConfiguration();
        Map<String, Object> verifiedBody;
        try {
            String timestamp=java.time.LocalDateTime.now(ZoneId.of("Africa/Nairobi")).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            String password=Base64.getEncoder().encodeToString((mpesaShortcode+mpesaPasskey+timestamp).getBytes(StandardCharsets.UTF_8));
            Map<String,String> query=Map.of("BusinessShortCode",mpesaShortcode,"Password",password,"Timestamp",timestamp,"CheckoutRequestID",callback.getCheckoutRequestId());
            HttpHeaders headers=new HttpHeaders(); headers.setBasicAuth(mpesaConsumerKey,mpesaConsumerSecret);
            ResponseEntity<Map<String, Object>> token = darajaRestTemplate.exchange(
                    mpesaAuthUrl, HttpMethod.GET, new HttpEntity<>(headers),
                    new ParameterizedTypeReference<>() {});
            HttpHeaders queryHeaders=new HttpHeaders(); queryHeaders.setBearerAuth(String.valueOf(token.getBody().get("access_token"))); queryHeaders.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<Map<String, Object>> verified = darajaRestTemplate.exchange(
                    mpesaStkQueryUrl, HttpMethod.POST, new HttpEntity<>(query, queryHeaders),
                    new ParameterizedTypeReference<>() {});
            verifiedBody = verified.getBody();
            if (verifiedBody == null
                    || !String.valueOf(callback.getResultCode()).equals(String.valueOf(verifiedBody.get("ResultCode")))
                    || !callback.getCheckoutRequestId().equals(String.valueOf(verifiedBody.get("CheckoutRequestID")))
                    || !callback.getMerchantRequestId().equals(String.valueOf(verifiedBody.get("MerchantRequestID")))) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "M-Pesa callback could not be reconciled");
            }
        } catch (ApiException e) { throw e; } catch (Exception e) { throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED,"M-Pesa payment verification failed"); }
        // A request accepted by Daraja whose HTTP response timed out can still
        // be correlated safely from the paymentId embedded in its callback URL
        // and the IDs returned by the independent Daraja query.
        payment.setMpesaCheckoutRequestId(callback.getCheckoutRequestId());
        payment.setMpesaMerchantRequestId(callback.getMerchantRequestId());
        payment.setConfirmedAt(OffsetDateTime.now());
        Checkout checkout=payment.getCheckout();
        if(callback.getResultCode()==0) {
            String amount=null, receipt=null;
            if(callback.getCallbackMetadata()!=null && callback.getCallbackMetadata().getItem()!=null) for(var item:callback.getCallbackMetadata().getItem()) { if("Amount".equals(item.getName())) amount=String.valueOf(item.getValue()); if("MpesaReceiptNumber".equals(item.getName())) receipt=String.valueOf(item.getValue()); }
            int expectedAmount = requireWholeKes(payment.getAmount(), payment.getCurrency());
            if (amount == null || new BigDecimal(amount).compareTo(BigDecimal.valueOf(expectedAmount)) != 0) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "M-Pesa success amount does not match checkout");
            }
            payment.setStatus(CheckoutPaymentStatus.SUCCESS);
            // Only trust receipt metadata when the optional shared-secret header
            // was actually presented and validated. Daraja query IDs remain the
            // source of truth for normal headerless callbacks.
            if (receipt != null && suppliedSecret != null && !suppliedSecret.isBlank()) payment.setProviderReference(receipt);
            else payment.setProviderReference(callback.getCheckoutRequestId());
            checkout.setStatus(CheckoutStatus.PAID); recordAllocations(payment);
            publishPaymentVerified(payment);
        } else { payment.setStatus(callback.getResultCode()==1032 ? CheckoutPaymentStatus.CANCELLED : CheckoutPaymentStatus.FAILED); payment.setFailureReason(callback.getResultDesc()); checkout.setStatus(CheckoutStatus.PAYMENT_FAILED); }
        checkoutPaymentRepository.save(payment); checkoutRepository.save(checkout);
    }
    public SupplierFinancialStatusResponse supplierFinancialStatus(AuthenticatedUser actor, UUID purchaseOrderId) {
        requireManufacturer(actor);
        PurchaseOrder order = purchaseOrderRepository.findByIdWithItems(purchaseOrderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Purchase order not found"));
        if (!order.getSupplierOrganization().getId().equals(actor.organizationId())) throw new ApiException(ErrorCode.ACCESS_DENIED);
        PaymentAllocation allocation = allocationRepository.findByPurchaseOrder_Id(purchaseOrderId).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Payment allocation not found"));
        SupplierSettlement settlement = supplierSettlementRepository.findByAllocation_Id(allocation.getId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Supplier settlement not found"));
        CheckoutPayment payment = allocation.getPayment();
        return new SupplierFinancialStatusResponse(order.getId(), payment.getCheckout().getCheckoutReference(), allocation.getId(),
                allocation.getGrossAmount(), allocation.getProviderFee(), allocation.getCommission(), allocation.getSupplierAmount(),
                allocation.getCurrency(), payment.getStatus().name(), settlement.getStatus().name(), payment.isDemo(),
                payment.getDemoLabel(), settlement.getNote());
    }

    @Transactional(readOnly = true)
    public CheckoutResponse.SupplierOrder manufacturerOrder(AuthenticatedUser actor, UUID purchaseOrderId) {
        requireManufacturer(actor);
        PurchaseOrder order = purchaseOrderRepository.findByIdWithItems(purchaseOrderId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Purchase order not found"));
        if (!order.getSupplierOrganization().getId().equals(actor.organizationId())) throw new ApiException(ErrorCode.ACCESS_DENIED);
        return supplierOrder(order);
    }

    @Transactional(readOnly = true)
    public List<CheckoutResponse.Allocation> allocations(AuthenticatedUser actor, UUID checkoutId) {
        requireDistributor(actor);
        Checkout checkout = checkoutRepository.findByIdAndBuyerOrganization_Id(checkoutId, actor.organizationId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Checkout not found"));
        CheckoutPayment payment = checkout.getPayment();
        if (payment == null) return List.of();
        return allocationRepository.findByPayment_IdOrderByCreatedAtAsc(payment.getId()).stream().map(this::allocationResponse).toList();
    }

    @Scheduled(fixedDelayString = "${logiflow.demo-payments.poll-interval-ms:1000}")
    @Transactional
    public void simulatePendingDemoPayments() {
        if (!demoEnabled) return;
        OffsetDateTime cutoff = OffsetDateTime.now().minusSeconds(confirmationDelaySeconds);
        for (CheckoutPayment payment : checkoutPaymentRepository.findByDemoTrueAndStatusAndInitiatedAtBefore(CheckoutPaymentStatus.PENDING, cutoff)) {
            payment = checkoutPaymentRepository.findByIdForUpdate(payment.getId()).orElse(null);
            if (payment == null || payment.getStatus() != CheckoutPaymentStatus.PENDING) continue;
            Checkout checkout = payment.getCheckout();
            String outcome = demoOutcome.toUpperCase(Locale.ROOT);
            payment.setProviderReference("DEMO-" + UUID.randomUUID().toString().toUpperCase(Locale.ROOT));
            payment.setConfirmedAt(OffsetDateTime.now());
            CheckoutStatus checkoutStatus;
            switch (outcome) {
                case "SUCCESS" -> {
                    payment.setStatus(CheckoutPaymentStatus.SUCCESS);
                    checkout.setStatus(CheckoutStatus.PAID);
                    recordAllocations(payment);
                    publishPaymentVerified(payment);
                    checkoutStatus = CheckoutStatus.PAID;
                }
                case "FAILED" -> { payment.setStatus(CheckoutPaymentStatus.FAILED); checkoutStatus = CheckoutStatus.PAYMENT_FAILED; }
                case "CANCELLED" -> { payment.setStatus(CheckoutPaymentStatus.CANCELLED); checkoutStatus = CheckoutStatus.PAYMENT_FAILED; }
                default -> { payment.setStatus(CheckoutPaymentStatus.TIMED_OUT); checkoutStatus = CheckoutStatus.PAYMENT_FAILED; }
            }
            checkout.setStatus(checkoutStatus);
            checkoutPaymentRepository.save(payment);
            checkout.setPayment(payment);
            checkoutRepository.save(checkout);
        }
    }

    private void publishPaymentVerified(CheckoutPayment payment) {
        if (eventPublisher == null) {
            purchaseOrderShipmentService.markCheckoutOrdersReady(payment);
            return;
        }
        eventPublisher.publishEvent(PurchaseOrderPaymentVerifiedEvent.checkout(payment.getId()));
    }

    private void recordAllocations(CheckoutPayment payment) {
        for (PurchaseOrder order : purchaseOrderRepository.findByCheckout_Id(payment.getCheckout().getId())) {
            PaymentAllocation allocation = new PaymentAllocation();
            allocation.setPayment(payment);
            allocation.setPurchaseOrder(order);
            allocation.setSupplierOrganization(order.getSupplierOrganization());
            allocation.setGrossAmount(order.getTotalAmount());
            allocation.setProviderFee(BigDecimal.ZERO);
            allocation.setCommission(BigDecimal.ZERO);
            allocation.setSupplierAmount(order.getTotalAmount());
            allocation.setCurrency(order.getCurrency());
            allocation.setDemo(payment.isDemo());
            allocation = allocationRepository.save(allocation);
            if (!payment.isDemo()) {
                FinancialLedgerService.FinancialBreakdown breakdown = financialLedgerService.recordCapturedPayment(
                        "CHECKOUT_ALLOCATION", allocation.getId(), order.getSupplierOrganization(),
                        allocation.getGrossAmount(), allocation.getProviderFee(), allocation.getCurrency(), false);
                allocation.setProviderFee(breakdown.providerFee());
                allocation.setCommission(breakdown.commission());
                allocation.setSupplierAmount(breakdown.supplierAmount());
                allocationRepository.save(allocation);
            }
            SupplierSettlement settlement = new SupplierSettlement();
            settlement.setAllocation(allocation);
            settlement.setSupplierOrganization(order.getSupplierOrganization());
            settlement.setAmount(allocation.getSupplierAmount());
            settlement.setCurrency(allocation.getCurrency());
            settlement.setStatus(SupplierSettlementStatus.PENDING);
            settlement.setDemo(payment.isDemo());
            settlement.setNote(payment.isDemo()
                    ? "DEMO allocation only; no supplier payout was made"
                    : "Checkout allocation recorded; supplier payout has not been confirmed");
            supplierSettlementRepository.save(settlement);
        }
    }

    private void cancelAndRelease(Checkout checkout) {
        for (PurchaseOrder order : purchaseOrderRepository.findByCheckout_Id(checkout.getId())) {
            if (order.isStockReserved()) {
                order.getItems().forEach(item -> {
                    StockItem stock = stockItemRepository.findByIdAndOrganizationIdForUpdate(
                            item.getStockItem().getId(), order.getSupplierOrganization().getId())
                            .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND));
                    if (stock.getReservedQuantity() < item.getQuantity()) throw new ApiException(ErrorCode.CONFLICT, "Reserved stock is inconsistent during checkout cancellation");
                    stock.setReservedQuantity(stock.getReservedQuantity() - item.getQuantity());
                });
                order.setStockReserved(false);
            }
            order.setStatus(PurchaseOrderStatus.CANCELLED);
            purchaseOrderRepository.save(order);
        }
    }

    private CheckoutResponse response(Checkout checkout) {
        CheckoutPayment payment = checkout.getPayment();
        CheckoutResponse.Payment paymentResponse = payment == null ? null : paymentResponse(checkout, payment);
        List<CheckoutResponse.SupplierOrder> orderResponses = purchaseOrderRepository.findByCheckout_Id(checkout.getId()).stream().map(this::supplierOrder).toList();
        List<CheckoutResponse.Allocation> allocations = payment == null ? List.of()
                : allocationRepository.findByPayment_IdOrderByCreatedAtAsc(payment.getId()).stream().map(this::allocationResponse).toList();
        return new CheckoutResponse(checkout.getId(), checkout.getCheckoutReference(), checkout.getStatus().name(),
                checkout.getTotalAmount(), checkout.getCurrency(), checkout.getBuyerOrganization().getId(),
                safeName(checkout.getBuyerOrganization().getName()), payment != null && payment.isDemo(),
                payment == null ? null : payment.getDemoLabel(), paymentResponse, orderResponses, allocations);
    }

    private CheckoutResponse.Payment paymentResponse(Checkout checkout, CheckoutPayment payment) {
        CheckoutPaymentStatus status = payment.getStatus();
        boolean terminal = status == CheckoutPaymentStatus.SUCCESS || status == CheckoutPaymentStatus.FAILED
                || status == CheckoutPaymentStatus.CANCELLED || status == CheckoutPaymentStatus.TIMED_OUT;
        boolean retryAllowed = status == CheckoutPaymentStatus.FAILED || status == CheckoutPaymentStatus.CANCELLED
                || status == CheckoutPaymentStatus.TIMED_OUT;
        String message = switch (status) {
            case PENDING -> "Payment is awaiting provider confirmation.";
            case SUCCESS -> "Payment confirmed.";
            case FAILED -> "Payment failed. You can try another payment method.";
            case CANCELLED -> "Payment was cancelled. You can try another payment method.";
            case TIMED_OUT -> "Payment attempt timed out. Retry is available after the provider confirmed the outcome.";
            case UNKNOWN -> "Payment status cannot currently be established. Do not retry until confirmation is available.";
        };
        return new CheckoutResponse.Payment(payment.getId(), checkout.getCheckoutReference(),
                payment.isDemo() ? "DEMO" : payment.getMethod().name(),
                payment.getProvider() == null ? null : payment.getProvider().name(), status.name(), payment.getProviderReference(),
                payment.getProviderAuthorizationUrl(), payment.getProviderAccessCode(),
                payment.isDemo(), payment.getDemoLabel(), payment.getInitiatedAt(), payment.getConfirmedAt(),
                payment.getUpdatedAt(), terminal, retryAllowed, message);
    }

    private CheckoutResponse.SupplierOrder supplierOrder(PurchaseOrder order) {
        List<CheckoutItem> lines = checkoutItemRepository.findByPurchaseOrder_Id(order.getId());
        UUID warehouseId = lines.stream().map(CheckoutItem::getDestinationWarehouse).filter(Objects::nonNull)
                .map(Warehouse::getId).findFirst().orElse(null);
        return new CheckoutResponse.SupplierOrder(order.getId(), order.getPurchaseOrderNumber(),
                order.getSupplierOrganization().getId(), safeName(order.getSupplierOrganization().getName()), warehouseId,
                order.getStatus().name(), order.getTotalAmount(), order.getCurrency(),
                lines.stream().map(line -> {
                    StockItem product = order.getItems().stream().map(PurchaseOrderItem::getStockItem)
                            .filter(candidate -> candidate.getId().equals(line.getProductId())).findFirst().orElse(null);
                    return new CheckoutResponse.Item(line.getProductId(), line.getProductNameSnapshot(),
                            product == null ? null : product.getSku(),
                            product == null ? null : com.example.distrobackend.dto.ProductResponse.from(product).imageUrl(),
                            line.getQuantity(), line.getUnitPrice(), line.getLineTotal());
                }).toList());
    }
    private CheckoutResponse.Allocation allocationResponse(PaymentAllocation allocation) {
        SupplierSettlement settlement = supplierSettlementRepository.findByAllocation_Id(allocation.getId()).orElse(null);
        return new CheckoutResponse.Allocation(allocation.getId(), allocation.getPurchaseOrder().getId(),
                allocation.getSupplierOrganization().getId(), safeName(allocation.getSupplierOrganization().getName()),
                allocation.getGrossAmount(), allocation.getProviderFee(), allocation.getCommission(), allocation.getSupplierAmount(),
                allocation.getCurrency(), allocation.isDemo(), settlement == null ? "PENDING" : settlement.getStatus().name(),
                settlement == null ? null : settlement.getNote());
    }

    private void ensureDemoAuthorized(AuthenticatedUser actor) {
        if (!demoEnabled) throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, "DEMO checkout payment is disabled");
        if (!allowedDemoOrganizationIds().contains(actor.organizationId())) throw new ApiException(ErrorCode.ACCESS_DENIED, "Organization is not enabled for DEMO payments");
    }

    private Set<UUID> allowedDemoOrganizationIds() {
        Set<UUID> allowed = new HashSet<>();
        for (String value : demoOrganizationIds.split(",")) {
            String id = value.trim();
            if (id.isEmpty()) continue;
            try {
                allowed.add(UUID.fromString(id));
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("LOGIFLOW_DEMO_ORGANIZATION_IDS must contain comma-separated organization UUIDs", ex);
            }
        }
        if (allowed.isEmpty()) {
            throw new IllegalStateException("LOGIFLOW_DEMO_ORGANIZATION_IDS must contain at least one organization UUID when DEMO payments are enabled");
        }
        return allowed;
    }

    private void requireDistributor(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null || actor.role() == null || actor.role().organizationtype() != Organizationtype.DISTRIBUTOR)
            throw new ApiException(ErrorCode.ACCESS_DENIED, "A distributor organization identity is required");
    }
    private void requireManufacturer(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null || actor.role() == null || actor.role().organizationtype() != Organizationtype.MANUFACTURER)
            throw new ApiException(ErrorCode.ACCESS_DENIED, "A manufacturer organization identity is required");
    }
    private static String safeName(String name) { return name == null || name.isBlank() ? "Organization details unavailable" : name; }
    private static String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
