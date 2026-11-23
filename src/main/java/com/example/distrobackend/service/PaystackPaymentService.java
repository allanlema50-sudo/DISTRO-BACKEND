package com.example.distrobackend.service;

import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Domain.enums.PayoutMethod;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;

import jakarta.annotation.PostConstruct;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Provider boundary for Paystack checkout initialization and signed webhooks. */
@Service
public class PaystackPaymentService {
    private static final String PAYSTACK_HOST = "api.paystack.co";
    private static final Pattern REFERENCE = Pattern.compile("^[A-Za-z0-9.=_-]{1,120}$");
    private static final Pattern TRANSFER_REFERENCE = Pattern.compile("^[a-z0-9_-]{16,50}$");
    private static final Pattern HEX_SIGNATURE = Pattern.compile("^[0-9a-fA-F]{128}$");
    private static final long WEBHOOK_TOLERANCE_SECONDS = 300;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final boolean transfersEnabled;
    private final String baseUrl;
    private final String secretKey;
    private final String webhookSecret;
    private final String callbackUrl;

    public PaystackPaymentService(
            @Qualifier("darajaRestTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${paystack.enabled:false}") boolean enabled,
            @Value("${paystack.transfers-enabled:false}") boolean transfersEnabled,
            @Value("${paystack.base-url:https://api.paystack.co}") String baseUrl,
            @Value("${paystack.secret-key:}") String secretKey,
            @Value("${paystack.webhook-secret:}") String webhookSecret,
            @Value("${paystack.callback-url:}") String callbackUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.transfersEnabled = transfersEnabled;
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.webhookSecret = webhookSecret == null ? "" : webhookSecret.trim();
        this.callbackUrl = callbackUrl == null ? "" : callbackUrl.trim();
    }

    @PostConstruct
    void validateConfiguration() {
        if (transfersEnabled && !enabled) {
            throw new IllegalStateException("PAYSTACK_ENABLED must be true when PAYSTACK_TRANSFERS_ENABLED=true");
        }
        if (!enabled) return;
        validateProviderHost();
        if (secretKey.isBlank()) {
            throw new IllegalStateException("PAYSTACK_SECRET_KEY must be set when PAYSTACK_ENABLED=true");
        }
        if (webhookSecret.isBlank()) {
            throw new IllegalStateException("PAYSTACK_WEBHOOK_SECRET must be set when PAYSTACK_ENABLED=true");
        }
        if (!callbackUrl.isBlank()) validateCallbackUrl();
    }

    public boolean enabled() {
        return enabled;
    }

    public Initialization initialize(String reference, BigDecimal amount, String currency,
                                    String email, String channel, String paymentId,
                                    String checkoutReference) {
        requireEnabled();
        if (!REFERENCE.matcher(reference).matches()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Paystack payment reference is invalid");
        }
        if (email == null || email.isBlank()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "A customer email is required for Paystack checkout");
        }
        long minorAmount = toMinorAmount(amount, currency);
        String normalizedCurrency = currency.trim().toUpperCase(Locale.ROOT);
        String normalizedChannel = normalizeChannel(channel);
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("amount", minorAmount);
        body.put("email", email.trim());
        body.put("currency", normalizedCurrency);
        body.put("reference", reference);
        body.put("channels", List.of(normalizedChannel));
        try {
            body.put("metadata", objectMapper.writeValueAsString(metadata(paymentId, checkoutReference)));
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to encode Paystack payment metadata", ex);
        }
        if (!callbackUrl.isBlank()) body.put("callback_url", callbackUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(secretKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(
                    URI.create(baseUrl + "/transaction/initialize"), HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class);
        } catch (HttpStatusCodeException ex) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED,
                    "Paystack rejected the payment initialization request");
        } catch (RestClientException ex) {
            throw new ProviderOutcomeUnknownException("Paystack initialization outcome is unknown", ex);
        }

        JsonNode root;
        try {
            root = response.getBody() == null ? null : objectMapper.readTree(response.getBody());
        } catch (IOException ex) {
            throw new ProviderOutcomeUnknownException("Paystack returned an invalid response", ex);
        }
        if (root == null || !root.path("status").asBoolean(false)) {
            String message = root == null ? "Paystack returned an empty response"
                    : root.path("message").asText("Paystack rejected the payment request");
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, message);
        }
        JsonNode data = root.path("data");
        String providerReference = data.path("reference").asText("");
        String authorizationUrl = data.path("authorization_url").asText("");
        String accessCode = data.path("access_code").asText("");
        if (providerReference.isBlank() || authorizationUrl.isBlank() || accessCode.isBlank()) {
            throw new ProviderOutcomeUnknownException("Paystack returned an incomplete initialization response", null);
        }
        return new Initialization(providerReference, authorizationUrl, accessCode, normalizedChannel);
    }

    public Webhook parseAndVerifyWebhook(byte[] rawBody, String paystackSignature,
                                         String legacySignature) {
        requireEnabled();
        if (rawBody == null || rawBody.length == 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Paystack webhook body is required");
        }
        if (!verifySignature(rawBody, paystackSignature, legacySignature)) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid Paystack webhook signature");
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode data = root.path("data");
            String event = root.path("event").asText("").trim();
            String reference = data.path("reference").asText("").trim();
            JsonNode disputeTransaction = data.path("transaction");
            if (reference.isBlank() && !disputeTransaction.isMissingNode()) {
                reference = disputeTransaction.path("reference").asText("").trim();
            }
            String disputeId = data.path("id").isMissingNode() ? null : data.path("id").asText();
            boolean disputeEvent = event.startsWith("charge.dispute.");
            if (disputeEvent && reference.isBlank() && disputeId != null) reference = disputeId;
            if (event.isBlank() || reference.isBlank() || (disputeEvent && disputeId == null)) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Paystack webhook is missing event or reference");
            }
            String dueAtValue = data.path("due_at").asText(null);
            java.time.OffsetDateTime dueAt = null;
            if (dueAtValue != null && !dueAtValue.isBlank()) {
                try {
                    dueAt = java.time.OffsetDateTime.parse(dueAtValue);
                } catch (java.time.format.DateTimeParseException ex) {
                    throw new ApiException(ErrorCode.BAD_REQUEST, "Paystack dispute due_at is invalid");
                }
            }
            return new Webhook(event, reference, data.path("status").asText(""),
                    data.path("amount").isNumber() ? data.path("amount").longValue()
                            : data.path("refund_amount").isNumber() ? data.path("refund_amount").longValue() : null,
                    data.path("currency").asText(null), data.path("gateway_response").asText(null),
                    data.path("transfer_code").asText(null), disputeId,
                    data.path("resolution").asText(null), dueAt,
                    disputeTransaction.path("reference").asText(reference));
        } catch (IOException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Paystack webhook body is not valid JSON");
        }
    }

    private boolean verifySignature(byte[] rawBody, String structuredSignature, String legacySignature) {
        if (structuredSignature != null && !structuredSignature.isBlank()) {
            String timestamp = null;
            List<String> signatures = new ArrayList<>();
            for (String part : structuredSignature.split(",")) {
                String[] pair = part.trim().split("=", 2);
                if (pair.length != 2) continue;
                if ("t".equals(pair[0])) timestamp = pair[1];
                if ("v1".equals(pair[0])) {
                    String[] keyedSignature = pair[1].split(":", 2);
                    signatures.add(keyedSignature.length == 2 ? keyedSignature[1] : pair[1]);
                }
            }
            if (timestamp == null || signatures.isEmpty()) return false;
            long signedAt;
            try {
                signedAt = Long.parseLong(timestamp);
            } catch (NumberFormatException ex) {
                return false;
            }
            if (Math.abs(Instant.now().getEpochSecond() - signedAt) > WEBHOOK_TOLERANCE_SECONDS) return false;
            byte[] expected = hmac((timestamp + ".").getBytes(StandardCharsets.UTF_8), rawBody, webhookSecret);
            return signatures.stream().anyMatch(value -> matchesHex(expected, value));
        }
        if (legacySignature == null || !HEX_SIGNATURE.matcher(legacySignature.trim()).matches()) return false;
        return matchesHex(hmac(new byte[0], rawBody, secretKey), legacySignature.trim());
    }

    private byte[] hmac(byte[] prefix, byte[] rawBody, String signingSecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            mac.update(prefix);
            mac.update(rawBody);
            return mac.doFinal();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to verify Paystack webhook signature", ex);
        }
    }

    private static boolean matchesHex(byte[] expected, String candidate) {
        try {
            byte[] actual = java.util.HexFormat.of().parseHex(candidate);
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static long toMinorAmount(BigDecimal amount, String currency) {
        if (amount == null || amount.signum() <= 0 || currency == null
                || !"KES".equalsIgnoreCase(currency.trim())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Paystack checkout must be a positive KES amount");
        }
        try {
            return amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Paystack checkout amount must have at most two decimals");
        }
    }

    private static String normalizeChannel(String channel) {
        String normalized = channel == null || channel.isBlank() ? "card" : channel.trim().toLowerCase(Locale.ROOT);
        if (!Set.of("card", "mobile_money", "bank_transfer", "bank", "ussd", "qr", "eft").contains(normalized)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Unsupported Paystack payment channel");
        }
        return normalized;
    }

    private static Map<String, String> metadata(String paymentId, String checkoutReference) {
        return Map.of("paymentId", paymentId, "checkoutReference", checkoutReference);
    }

    private void requireEnabled() {
        if (!enabled) throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, "Paystack payments are disabled");
        validateProviderHost();
    }

    private void requireTransfersEnabled() {
        requireEnabled();
        if (!transfersEnabled) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, "Paystack transfers are disabled");
        }
    }

    private void validateProviderHost() {
        try {
            URI uri = URI.create(baseUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || !PAYSTACK_HOST.equalsIgnoreCase(uri.getHost())
                    || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
        } catch (RuntimeException ex) {
            throw new IllegalStateException("PAYSTACK_BASE_URL must be https://api.paystack.co", ex);
        }
    }

    private void validateCallbackUrl() {
        try {
            URI uri = URI.create(callbackUrl);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null) throw new IllegalArgumentException();
        } catch (RuntimeException ex) {
            throw new IllegalStateException("PAYSTACK_CALLBACK_URL must be a public HTTPS URL", ex);
        }
    }

    private static String trimTrailingSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    public Recipient createRecipient(PayoutMethod method, String accountName, String accountNumber,
                                     String bankCode, String currency, String email) {
        requireTransfersEnabled();
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("type", method == PayoutMethod.MOBILE_MONEY ? "mobile_money" : "kepss");
        body.put("name", accountName);
        body.put("account_number", accountNumber);
        body.put("currency", currency.toUpperCase(Locale.ROOT));
        if (bankCode != null && !bankCode.isBlank()) body.put("bank_code", bankCode.trim());
        if (email != null && !email.isBlank()) body.put("email", email.trim());
        JsonNode data = postJson("/transferrecipient", body, "Paystack recipient creation failed");
        String recipientCode = data.path("data").path("recipient_code").asText("");
        if (recipientCode.isBlank()) {
            throw new ProviderOutcomeUnknownException("Paystack returned no recipient code", null);
        }
        return new Recipient(recipientCode);
    }

    public Transfer initiateTransfer(String reference, BigDecimal amount, String currency,
                                     String recipientCode, String reason) {
        requireTransfersEnabled();
        if (!TRANSFER_REFERENCE.matcher(reference).matches()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Settlement transfer reference is invalid");
        }
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("source", "balance");
        body.put("amount", toMinorAmount(amount, currency));
        body.put("currency", currency.toUpperCase(Locale.ROOT));
        body.put("recipient", recipientCode);
        body.put("reference", reference);
        body.put("reason", reason);
        JsonNode data = postJson("/transfer", body, "Paystack transfer creation failed");
        JsonNode transfer = data.path("data");
        String returnedReference = transfer.path("reference").asText(reference);
        String status = transfer.path("status").asText("");
        String transferCode = transfer.path("transfer_code").asText(null);
        if (returnedReference.isBlank() || status.isBlank()) {
            throw new ProviderOutcomeUnknownException("Paystack returned an incomplete transfer response", null);
        }
        return new Transfer(returnedReference, transferCode, status);
    }

    public Verification verifyTransaction(String reference) {
        requireEnabled();
        return verification(getJson("/transaction/verify/" + encodePath(reference),
                "Paystack transaction verification failed"), false);
    }

    public Verification verifyTransfer(String reference) {
        requireTransfersEnabled();
        return verification(getJson("/transfer/verify/" + encodePath(reference),
                "Paystack transfer verification failed"), true);
    }

    public Refund createRefund(String reference, String transactionReference, BigDecimal amount,
                               String currency, String customerNote, String merchantNote) {
        requireEnabled();
        if (reference == null || !reference.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Paystack refund reference is invalid");
        }
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("transaction", transactionReference);
        body.put("amount", toMinorAmount(amount, currency));
        body.put("currency", currency.toUpperCase(Locale.ROOT));
        body.put("reference", reference);
        body.put("customer_note", customerNote);
        body.put("merchant_note", merchantNote);
        JsonNode data = postJson("/refund", body, "Paystack rejected the refund request").path("data");
        String returnedReference = data.path("reference").asText("");
        String status = data.path("status").asText("");
        if (!reference.equalsIgnoreCase(returnedReference) || status.isBlank()) {
            throw new ProviderOutcomeUnknownException("Paystack returned an incomplete refund response", null);
        }
        return new Refund(returnedReference, data.path("id").asText(null), status,
                data.path("amount").isNumber() ? data.path("amount").longValue() : null,
                data.path("currency").asText(null));
    }

    public Refund verifyRefund(String reference) {
        requireEnabled();
        JsonNode root = getJson("/refund?reference=" + encodePath(reference),
                "Paystack refund verification failed");
        JsonNode refunds = root.path("data");
        if (!refunds.isArray()) throw new ProviderOutcomeUnknownException("Paystack refund response was incomplete", null);
        for (JsonNode refund : refunds) {
            if (reference.equalsIgnoreCase(refund.path("reference").asText(""))) {
                return new Refund(reference, refund.path("id").asText(null), refund.path("status").asText(""),
                        refund.path("amount").isNumber() ? refund.path("amount").longValue() : null,
                        refund.path("currency").asText(null));
            }
        }
        throw new ProviderOutcomeUnknownException("Paystack has not returned the refund reference yet", null);
    }

    public ProviderTransactionPage listTransactions(java.time.OffsetDateTime from,
                                                     java.time.OffsetDateTime to,
                                                     int page, int perPage) {
        requireEnabled();
        if (from == null || to == null || !from.isBefore(to) || page < 1
                || perPage < 1 || perPage > 100) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Paystack transaction report filters are invalid");
        }
        String path = "/transaction?from=" + encodePath(from.toString())
                + "&to=" + encodePath(to.toString())
                + "&page=" + page + "&perPage=" + perPage;
        JsonNode root = getJson(path, "Paystack transaction list request failed");
        JsonNode data = root.path("data");
        if (!data.isArray()) {
            throw new ProviderOutcomeUnknownException("Paystack transaction list response was incomplete", null);
        }
        List<ProviderTransaction> transactions = new ArrayList<>();
        for (JsonNode item : data) {
            String reference = item.path("reference").asText("").trim();
            String status = item.path("status").asText("").trim();
            if (reference.isBlank() || status.isBlank() || !item.path("amount").canConvertToLong()) {
                throw new ProviderOutcomeUnknownException("Paystack transaction row is missing required fields", null);
            }
            JsonNode id = item.path("id");
            transactions.add(new ProviderTransaction(id.isMissingNode() ? null : id.asText(), reference,
                    status, item.path("amount").longValue(), item.path("currency").asText(null),
                    item.path("paid_at").asText(null)));
        }
        JsonNode meta = root.path("meta");
        int returnedPage = meta.path("page").canConvertToInt() ? meta.path("page").intValue() : page;
        int returnedPerPage = meta.path("perPage").canConvertToInt()
                ? meta.path("perPage").intValue() : perPage;
        long total = meta.path("total").canConvertToLong() ? meta.path("total").longValue() : -1;
        return new ProviderTransactionPage(transactions, returnedPage, returnedPerPage, total);
    }

    private Verification verification(JsonNode root, boolean transfer) {
        JsonNode data = root.path("data");
        String reference = data.path("reference").asText("");
        String status = data.path("status").asText("");
        if (reference.isBlank() || status.isBlank() || !root.path("status").asBoolean(false)) {
            throw new ProviderOutcomeUnknownException("Paystack returned an incomplete verification response", null);
        }
        return new Verification(reference, status,
                data.path("amount").isNumber() ? data.path("amount").longValue() : null,
                data.path("currency").asText(null), data.path("gateway_response").asText(null),
                transfer ? data.path("transfer_code").asText(null) : null);
    }

    private JsonNode getJson(String path, String rejectionMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(secretKey);
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(URI.create(baseUrl + path), HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
        } catch (HttpStatusCodeException ex) {
            throw new ProviderOutcomeUnknownException(rejectionMessage, ex);
        } catch (RestClientException ex) {
            throw new ProviderOutcomeUnknownException("Paystack verification outcome is unavailable", ex);
        }
        try {
            JsonNode root = response.getBody() == null ? null : objectMapper.readTree(response.getBody());
            if (root == null || !root.path("status").asBoolean(false)) {
                throw new ProviderOutcomeUnknownException(rejectionMessage, null);
            }
            return root;
        } catch (IOException ex) {
            throw new ProviderOutcomeUnknownException("Paystack returned invalid verification JSON", ex);
        }
    }

    private static String encodePath(String value) {
        if (value == null || value.isBlank() || value.length() > 120) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Paystack reference is invalid");
        }
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private JsonNode postJson(String path, Map<String, Object> body, String rejectionMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(secretKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(URI.create(baseUrl + path), HttpMethod.POST,
                    new HttpEntity<>(body, headers), String.class);
        } catch (HttpStatusCodeException ex) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, rejectionMessage);
        } catch (RestClientException ex) {
            throw new ProviderOutcomeUnknownException("Paystack request outcome is unknown", ex);
        }
        try {
            JsonNode root = response.getBody() == null ? null : objectMapper.readTree(response.getBody());
            if (root == null || !root.path("status").asBoolean(false)) {
                throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, rejectionMessage);
            }
            return root;
        } catch (IOException ex) {
            throw new ProviderOutcomeUnknownException("Paystack returned invalid JSON", ex);
        }
    }

    public record Initialization(String providerReference, String authorizationUrl, String accessCode, String channel) {}

    public record Recipient(String recipientCode) {}

    public record Transfer(String reference, String transferCode, String status) {}

    public record Verification(String reference, String status, Long amountMinor, String currency,
                               String gatewayResponse, String transferCode) {}

    public record Refund(String reference, String providerRefundId, String status,
                         Long amountMinor, String currency) {}

    public record ProviderTransaction(String providerId, String reference, String status,
                                      long amountMinor, String currency, String paidAt) {}

    public record ProviderTransactionPage(List<ProviderTransaction> transactions,
                                          int page, int perPage, long total) {}

    public record Webhook(String event, String reference, String status, Long amountMinor,
                           String currency, String gatewayResponse, String transferCode,
                           String disputeId, String disputeResolution, java.time.OffsetDateTime disputeDueAt,
                           String transactionReference) {
        public Webhook(String event, String reference, String status, Long amountMinor,
                       String currency, String gatewayResponse, String transferCode) {
            this(event, reference, status, amountMinor, currency, gatewayResponse, transferCode,
                    null, null, null, reference);
        }
    }

    public static class ProviderOutcomeUnknownException extends RuntimeException {
        public ProviderOutcomeUnknownException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
