package com.example.distrobackend.service;

import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Domain.enums.PayoutMethod;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
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
import java.util.Optional;
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
    private final JsonMapper jsonMapper;
    private final boolean enabled;
    private final boolean transfersEnabled;
    private final String mode;
    private final String baseUrl;
    private final String secretKey;
    private final String webhookSecret;
    private final String callbackUrl;

    public PaystackPaymentService(
            @Qualifier("darajaRestTemplate") RestTemplate restTemplate,
            JsonMapper jsonMapper,
            @Value("${paystack.enabled:false}") boolean enabled,
            @Value("${paystack.transfers-enabled:false}") boolean transfersEnabled,
            @Value("${paystack.mode:test}") String mode,
            @Value("${paystack.base-url:https://api.paystack.co}") String baseUrl,
            @Value("${paystack.secret-key:}") String secretKey,
            @Value("${paystack.webhook-secret:}") String webhookSecret,
            @Value("${paystack.callback-url:}") String callbackUrl) {
        this.restTemplate = restTemplate;
        this.jsonMapper = jsonMapper;
        this.enabled = enabled;
        this.transfersEnabled = transfersEnabled;
        this.mode = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
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
        String expectedKeyPrefix = switch (mode) {
            case "TEST" -> "sk_test_";
            case "LIVE" -> "sk_live_";
            default -> throw new IllegalStateException("PAYSTACK_MODE must be TEST or LIVE");
        };
        if (secretKey.isBlank()) {
            throw new IllegalStateException("PAYSTACK_SECRET_KEY must be set when PAYSTACK_ENABLED=true");
        }
        if (!secretKey.startsWith(expectedKeyPrefix)) {
            throw new IllegalStateException("PAYSTACK_SECRET_KEY does not match PAYSTACK_MODE=" + mode);
        }
        if (webhookSecret.isBlank()) {
            throw new IllegalStateException("PAYSTACK_WEBHOOK_SECRET must be set when PAYSTACK_ENABLED=true");
        }
        if (!callbackUrl.isBlank()) validateCallbackUrl();
    }

    public boolean enabled() {
        return enabled;
    }

    public boolean transfersEnabled() {
        return transfersEnabled;
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
            body.put("metadata", jsonMapper.writeValueAsString(metadata(paymentId, checkoutReference)));
        } catch (JacksonException ex) {
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
            if (ex.getStatusCode().is5xxServerError() || ex.getStatusCode().value() == 408) {
                throw new ProviderOutcomeUnknownException("Paystack initialization outcome is unknown", ex);
            }
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED,
                    "Paystack rejected the payment initialization request");
        } catch (RestClientException ex) {
            throw new ProviderOutcomeUnknownException("Paystack initialization outcome is unknown", ex);
        }

        JsonNode root;
        try {
            root = response.getBody() == null ? null : jsonMapper.readTree(response.getBody());
        } catch (JacksonException ex) {
            throw new ProviderOutcomeUnknownException("Paystack returned an invalid response", ex);
        }
        if (root == null || !root.path("status").isBoolean()) {
            throw new ProviderOutcomeUnknownException("Paystack returned an incomplete initialization response", null);
        }
        if (!root.path("status").asBoolean()) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED,
                    root.path("message").asString("Paystack rejected the payment request"));
        }
        JsonNode data = root.path("data");
        String providerReference = data.path("reference").asString("");
        String authorizationUrl = data.path("authorization_url").asString("");
        String accessCode = data.path("access_code").asString("");
        if (providerReference.isBlank() || !reference.equals(providerReference)
                || authorizationUrl.isBlank() || accessCode.isBlank()) {
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
            JsonNode root = jsonMapper.readTree(rawBody);
            JsonNode data = root.path("data");
            String event = root.path("event").asString("").trim();
            String reference = data.path("reference").asString("").trim();
            JsonNode disputeTransaction = data.path("transaction");
            String transactionReference = data.path("transaction_reference").asString("").trim();
            if (transactionReference.isBlank() && !disputeTransaction.isMissingNode()) {
                transactionReference = disputeTransaction.path("reference").asString("").trim();
            }
            if (reference.isBlank() && !disputeTransaction.isMissingNode()) {
                reference = disputeTransaction.path("reference").asString("").trim();
            }
            if (reference.isBlank() && event.startsWith("refund.")) reference = transactionReference;
            JsonNode disputeIdNode = data.path("id");
            String disputeId = disputeIdNode.isMissingNode() || disputeIdNode.isNull()
                    ? null : disputeIdNode.asString(null);
            boolean disputeEvent = event.startsWith("charge.dispute.");
            if (disputeEvent && reference.isBlank() && disputeId != null) reference = disputeId;
            if (event.isBlank() || reference.isBlank()
                    || (disputeEvent && (disputeId == null || disputeId.isBlank()))) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Paystack webhook is missing event or reference");
            }
            if (transactionReference.isBlank() && !event.startsWith("refund.")) {
                transactionReference = disputeTransaction.path("reference").asString(reference);
            }
            String dueAtValue = data.path("due_at").asString(null);
            java.time.OffsetDateTime dueAt = null;
            if (dueAtValue != null && !dueAtValue.isBlank()) {
                try {
                    dueAt = java.time.OffsetDateTime.parse(dueAtValue);
                } catch (java.time.format.DateTimeParseException ex) {
                    throw new ApiException(ErrorCode.BAD_REQUEST, "Paystack dispute due_at is invalid");
                }
            }
            return new Webhook(event, reference, data.path("status").asString(""),
                    parseMinorAmount(data),
                    data.path("currency").asString(null), data.path("gateway_response").asString(null),
                    data.path("transfer_code").asString(null), disputeId,
                    data.path("resolution").asString(null), dueAt,
                    transactionReference.isBlank() ? reference : transactionReference);
        } catch (JacksonException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Paystack webhook body is not valid JSON");
        }
    }

    private static Long parseMinorAmount(JsonNode data) {
        JsonNode amount = data.path("amount");
        if (amount.isMissingNode() || amount.isNull()) amount = data.path("refund_amount");
        if (amount.isMissingNode() || amount.isNull()) return null;
        if (amount.isIntegralNumber() && amount.canConvertToLong()) return amount.longValue();
        if (amount.isString()) {
            String value = amount.asString().trim();
            try {
                if (value.matches("-?[0-9]+")) return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                // Reject overflow below with the same safe validation error.
            }
        }
        throw new ApiException(ErrorCode.BAD_REQUEST, "Paystack webhook amount must be an integer minor-unit value");
    }

    public Verification verifyTransaction(String reference) {
        requireEnabled();
        if (reference == null || !REFERENCE.matcher(reference).matches()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Paystack payment reference is invalid");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(secretKey);
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(URI.create(baseUrl + "/transaction/verify/" + reference),
                    HttpMethod.GET, new HttpEntity<>(headers), String.class);
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) {
                return new Verification(reference, "not_found", null, null);
            }
            throw new ProviderOutcomeUnknownException("Paystack verification request failed", ex);
        } catch (RestClientException ex) {
            throw new ProviderOutcomeUnknownException("Paystack verification outcome is unknown", ex);
        }

        try {
            JsonNode root = response.getBody() == null ? null : jsonMapper.readTree(response.getBody());
            JsonNode data = root == null ? null : root.path("data");
            if (root == null || !root.path("status").asBoolean(false) || data == null || data.isMissingNode()) {
                throw new ProviderOutcomeUnknownException("Paystack returned an incomplete verification response", null);
            }
            String verifiedReference = data.path("reference").asString("");
            String status = data.path("status").asString("").trim().toLowerCase(Locale.ROOT);
            if (!reference.equals(verifiedReference) || status.isBlank()) {
                throw new ProviderOutcomeUnknownException("Paystack returned incomplete transaction verification", null);
            }
            Long amount = data.path("amount").canConvertToLong() ? data.path("amount").longValue() : null;
            String verifiedCurrency = data.path("currency").asString(null);
            return new Verification(verifiedReference, status, amount, verifiedCurrency);
        } catch (JacksonException ex) {
            throw new ProviderOutcomeUnknownException("Paystack returned invalid verification data", ex);
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
            throw new ApiException(ErrorCode.BAD_REQUEST, "Paystack payments require a positive KES amount");
        }
        try {
            return amount.movePointRight(2).setScale(0, RoundingMode.UNNECESSARY).longValueExact();
        } catch (ArithmeticException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Paystack amount must have at most two decimals");
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
        String normalizedCurrency = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if (method == PayoutMethod.MOBILE_MONEY && bankCode != null && !bankCode.isBlank()) {
            body.put("bank_code", bankCode.trim());
        } else if (method == PayoutMethod.MOBILE_MONEY && "KES".equals(normalizedCurrency)) {
            body.put("bank_code", "MPESA");
        } else if (bankCode != null && !bankCode.isBlank()) {
            body.put("bank_code", bankCode.trim());
        }
        if (email != null && !email.isBlank()) body.put("email", email.trim());
        JsonNode data = postJson("/transferrecipient", body, "Paystack recipient creation failed");
        String recipientCode = data.path("data").path("recipient_code").asString("");
        if (recipientCode.isBlank()) {
            throw new ProviderOutcomeUnknownException("Paystack returned no recipient code", null);
        }
        return new Recipient(recipientCode);
    }

    public Transfer initiateTransfer(String reference, BigDecimal amount, String currency,
                                     String recipientCode, String reason) {
        requireTransfersEnabled();
        if (reference == null || !TRANSFER_REFERENCE.matcher(reference).matches()) {
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
        String returnedReference = transfer.path("reference").asString("");
        String status = transfer.path("status").asString("");
        String transferCode = transfer.path("transfer_code").asString(null);
        if (!reference.equals(returnedReference) || status.isBlank()) {
            throw new ProviderOutcomeUnknownException("Paystack returned an incomplete transfer response", null);
        }
        return new Transfer(returnedReference, transferCode, status,
                transfer.path("amount").canConvertToLong() ? transfer.path("amount").longValue() : null,
                transfer.path("currency").asString(null));
    }

    public Optional<VerifiedTransfer> verifyTransfer(String reference) {
        requireTransfersEnabled();
        if (reference == null || !TRANSFER_REFERENCE.matcher(reference).matches()) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Settlement transfer reference is invalid");
        }
        JsonNode data = getVerifiedData("/transfer/verify/" + encodePath(reference),
                "Paystack transfer verification failed");
        if (data == null) return Optional.empty();
        String verifiedReference = data.path("reference").asString("");
        if (!reference.equals(verifiedReference)) {
            throw new ProviderOutcomeUnknownException("Paystack verified a different transfer reference", null);
        }
        if (data.path("status").asString("").isBlank()) {
            throw new ProviderOutcomeUnknownException("Paystack returned an incomplete transfer verification", null);
        }
        return Optional.of(new VerifiedTransfer(verifiedReference,
                data.path("transfer_code").asString(null), data.path("status").asString(""),
                data.path("amount").canConvertToLong() ? data.path("amount").longValue() : null,
                data.path("currency").asString(null), data.path("failures").asString(null)));
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
        String returnedReference = data.path("reference").asString("");
        String status = data.path("status").asString("");
        if (!reference.equalsIgnoreCase(returnedReference) || status.isBlank()) {
            throw new ProviderOutcomeUnknownException("Paystack returned an incomplete refund response", null);
        }
        return new Refund(returnedReference, data.path("id").asString(null), status,
                data.path("amount").isNumber() ? data.path("amount").longValue() : null,
                data.path("currency").asString(null));
    }

    public Refund verifyRefund(String reference) {
        requireEnabled();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(secretKey);
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(URI.create(baseUrl + "/refund?reference=" + encodePath(reference)),
                    HttpMethod.GET, new HttpEntity<>(headers), String.class);
        } catch (HttpStatusCodeException ex) {
            throw new ProviderOutcomeUnknownException("Paystack refund verification outcome is unknown", ex);
        } catch (RestClientException ex) {
            throw new ProviderOutcomeUnknownException("Paystack refund verification outcome is unknown", ex);
        }
        JsonNode root;
        try {
            root = response.getBody() == null ? null : jsonMapper.readTree(response.getBody());
        } catch (JacksonException ex) {
            throw new ProviderOutcomeUnknownException("Paystack returned invalid refund verification JSON", ex);
        }
        if (root == null || !root.path("status").asBoolean(false)) {
            throw new ProviderOutcomeUnknownException("Paystack refund verification response was incomplete", null);
        }
        JsonNode refunds = root.path("data");
        if (!refunds.isArray()) throw new ProviderOutcomeUnknownException("Paystack refund response was incomplete", null);
        for (JsonNode refund : refunds) {
            if (reference.equalsIgnoreCase(refund.path("reference").asString(""))) {
                return new Refund(reference, refund.path("id").asString(null), refund.path("status").asString(""),
                        refund.path("amount").isNumber() ? refund.path("amount").longValue() : null,
                        refund.path("currency").asString(null));
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
            String reference = item.path("reference").asString("").trim();
            String status = item.path("status").asString("").trim();
            if (reference.isBlank() || status.isBlank() || !item.path("amount").canConvertToLong()) {
                throw new ProviderOutcomeUnknownException("Paystack transaction row is missing required fields", null);
            }
            JsonNode id = item.path("id");
            transactions.add(new ProviderTransaction(id.isMissingNode() ? null : id.asString(), reference,
                    status, item.path("amount").longValue(), item.path("currency").asString(null),
                    item.path("paid_at").asString(null)));
        }
        JsonNode meta = root.path("meta");
        int returnedPage = meta.path("page").canConvertToInt() ? meta.path("page").intValue() : page;
        int returnedPerPage = meta.path("perPage").canConvertToInt()
                ? meta.path("perPage").intValue() : perPage;
        long total = meta.path("total").canConvertToLong() ? meta.path("total").longValue() : -1;
        return new ProviderTransactionPage(transactions, returnedPage, returnedPerPage, total);
    }

    private JsonNode getJson(String path, String rejectionMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(secretKey);
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(URI.create(baseUrl + path), HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().is5xxServerError() || ex.getStatusCode().value() == 408) {
                throw new ProviderOutcomeUnknownException("Paystack transaction-list outcome is unknown", ex);
            }
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, rejectionMessage);
        } catch (RestClientException ex) {
            throw new ProviderOutcomeUnknownException("Paystack transaction-list outcome is unknown", ex);
        }
        try {
            JsonNode root = response.getBody() == null ? null : jsonMapper.readTree(response.getBody());
            if (root == null || !root.path("status").isBoolean()) {
                throw new ProviderOutcomeUnknownException("Paystack returned an incomplete transaction-list response", null);
            }
            if (!root.path("status").asBoolean()) {
                throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, rejectionMessage);
            }
            return root;
        } catch (JacksonException ex) {
            throw new ProviderOutcomeUnknownException("Paystack returned invalid transaction-list JSON", ex);
        }
    }

    private JsonNode getVerifiedData(String path, String rejectionMessage) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(secretKey);
        ResponseEntity<String> response;
        try {
            response = restTemplate.exchange(URI.create(baseUrl + path), HttpMethod.GET,
                    new HttpEntity<>(headers), String.class);
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404) return null;
            if (ex.getStatusCode().is5xxServerError() || ex.getStatusCode().value() == 408) {
                throw new ProviderOutcomeUnknownException("Paystack transfer verification outcome is unknown", ex);
            }
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, rejectionMessage);
        } catch (RestClientException ex) {
            throw new ProviderOutcomeUnknownException("Paystack transfer verification outcome is unknown", ex);
        }
        try {
            JsonNode root = response.getBody() == null ? null : jsonMapper.readTree(response.getBody());
            if (root == null || !root.path("status").asBoolean(false) || !root.path("data").isObject()) {
                throw new ProviderOutcomeUnknownException("Paystack returned an incomplete transfer verification", null);
            }
            return root.path("data");
        } catch (JacksonException ex) {
            throw new ProviderOutcomeUnknownException("Paystack returned invalid transfer verification JSON", ex);
        }
    }

    private static String encodePath(String value) {
        return org.springframework.web.util.UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
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
            if (ex.getStatusCode().is5xxServerError() || ex.getStatusCode().value() == 408) {
                throw new ProviderOutcomeUnknownException("Paystack request outcome is unknown", ex);
            }
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, rejectionMessage);
        } catch (RestClientException ex) {
            throw new ProviderOutcomeUnknownException("Paystack request outcome is unknown", ex);
        }
        try {
            JsonNode root = response.getBody() == null ? null : jsonMapper.readTree(response.getBody());
            if (root == null || !root.path("status").isBoolean()) {
                throw new ProviderOutcomeUnknownException("Paystack returned an incomplete response", null);
            }
            if (!root.path("status").asBoolean()) {
                throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, rejectionMessage);
            }
            return root;
        } catch (JacksonException ex) {
            throw new ProviderOutcomeUnknownException("Paystack returned invalid JSON", ex);
        }
    }

    private void requireTransfersEnabled() {
        requireEnabled();
        if (!transfersEnabled) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED, "Paystack transfers are disabled");
        }
    }

    public record Initialization(String providerReference, String authorizationUrl, String accessCode, String channel) {}

    public record Recipient(String recipientCode) {}

    public record Transfer(String reference, String transferCode, String status,
                           Long amountMinor, String currency) {}

    public record VerifiedTransfer(String reference, String transferCode, String status,
                                   Long amountMinor, String currency, String failureReason) {}

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

    public record Verification(String reference, String status, Long amountMinor, String currency) {}

    public static class ProviderOutcomeUnknownException extends RuntimeException {
        public ProviderOutcomeUnknownException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
