package com.example.distrobackend.service;

import tools.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PaystackPaymentServiceTest {
    private static final String WEBHOOK_SECRET = "whsec_test_secret";

    @Test
    void initializesPaystackTransactionWithMinorKesAmountAndChannel() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        PaystackPaymentService service = service(restTemplate);
        server.expect(requestTo("https://api.paystack.co/transaction/initialize"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk_test_secret"))
                .andExpect(header("Content-Type", MediaType.APPLICATION_JSON_VALUE))
                .andRespond(withSuccess("""
                        {"status":true,"data":{"reference":"LF-123","authorization_url":"https://checkout.paystack.com/abc","access_code":"abc"}}
                        """, MediaType.APPLICATION_JSON));

        PaystackPaymentService.Initialization result = service.initialize(
                "LF-123", new BigDecimal("125.50"), "KES", "buyer@example.com",
                "card", "payment-id", "CHK-123");

        assertThat(result.providerReference()).isEqualTo("LF-123");
        assertThat(result.authorizationUrl()).isEqualTo("https://checkout.paystack.com/abc");
        assertThat(result.channel()).isEqualTo("card");
        server.verify();
    }

    @Test
    void verifiesTimestampedWebhookSignatureAndParsesChargeEvent() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        PaystackPaymentService service = service(restTemplate);
        byte[] body = "{\"event\":\"charge.success\",\"data\":{\"reference\":\"LF-123\",\"status\":\"success\",\"amount\":12550,\"currency\":\"KES\"}}"
                .getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signature = timestamp + "." + new Hmac(WEBHOOK_SECRET).hex((timestamp + ".").getBytes(StandardCharsets.UTF_8), body);

        PaystackPaymentService.Webhook webhook = service.parseAndVerifyWebhook(
                body, "t=" + timestamp + ",v1=whk_test:" + signature.substring(signature.indexOf('.') + 1), null);

        assertThat(webhook.event()).isEqualTo("charge.success");
        assertThat(webhook.reference()).isEqualTo("LF-123");
        assertThat(webhook.amountMinor()).isEqualTo(12550L);
    }

    @Test
    void verifiesLegacyTestWebhookWithTestSecretWhenSigningSecretIsUnavailable() throws Exception {
        byte[] body = "{\"event\":\"charge.success\",\"data\":{\"reference\":\"LF-LEGACY\",\"status\":\"success\",\"amount\":12550,\"currency\":\"KES\"}}"
                .getBytes(StandardCharsets.UTF_8);
        PaystackPaymentService service = service(new RestTemplate(), "TEST", "sk_test_secret", "");
        String signature = new Hmac("sk_test_secret").hex(new byte[0], body);

        PaystackPaymentService.Webhook webhook = service.parseAndVerifyWebhook(body, null, signature);

        assertThat(webhook.event()).isEqualTo("charge.success");
        assertThat(webhook.reference()).isEqualTo("LF-LEGACY");
    }

    @Test
    void acceptsLiveConfigurationWithoutSigningSecretAndUsesLegacySecretKeyVerification() throws Exception {
        byte[] body = "{\"event\":\"charge.success\",\"data\":{\"reference\":\"LF-LIVE\",\"status\":\"success\",\"amount\":12550,\"currency\":\"KES\"}}"
                .getBytes(StandardCharsets.UTF_8);
        PaystackPaymentService service = service(new RestTemplate(), "LIVE", "sk_live_secret", "");
        service.validateConfiguration();
        String signature = new Hmac("sk_live_secret").hex(new byte[0], body);

        PaystackPaymentService.Webhook webhook = service.parseAndVerifyWebhook(body, null, signature);

        assertThat(webhook.reference()).isEqualTo("LF-LIVE");
    }

    @Test
    void rejectsWebhookUrlConfiguredAsSigningSecret() {
        PaystackPaymentService service = service(new RestTemplate(), "TEST", "sk_test_secret",
                "https://api.example.test/webhook");

        assertThatThrownBy(service::validateConfiguration)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("whsec_");
    }

    @Test
    void rejectsTimestampedWebhookWhenSigningSecretIsMissing() {
        PaystackPaymentService service = service(new RestTemplate(), "TEST", "sk_test_secret", "");
        assertThatThrownBy(() -> service.parseAndVerifyWebhook(
                "{}".getBytes(StandardCharsets.UTF_8),
                "t=" + Instant.now().getEpochSecond() + ",v1=whk_test:00", null))
                .hasMessage("Invalid Paystack webhook signature");
    }

    @Test
    void rejectsInvalidWebhookSignature() {
        PaystackPaymentService service = service(new RestTemplate());
        assertThatThrownBy(() -> service.parseAndVerifyWebhook(
                "{}".getBytes(StandardCharsets.UTF_8),
                "t=" + Instant.now().getEpochSecond() + ",v1=whk_test:00", null))
                .hasMessage("Invalid Paystack webhook signature");
    }

    @Test
    void parsesFlatRefundTransactionReferenceAndStringMinorAmount() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        PaystackPaymentService service = service(restTemplate);
        byte[] body = "{\"event\":\"refund.processed\",\"data\":{\"transaction_reference\":\"TX-123\",\"amount\":\"12550\",\"currency\":\"KES\"}}"
                .getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signature = new Hmac(WEBHOOK_SECRET).hex((timestamp + ".").getBytes(StandardCharsets.UTF_8), body);

        PaystackPaymentService.Webhook webhook = service.parseAndVerifyWebhook(
                body, "t=" + timestamp + ",v1=whk_test:" + signature.substring(signature.indexOf('.') + 1), null);

        assertThat(webhook.reference()).isEqualTo("TX-123");
        assertThat(webhook.transactionReference()).isEqualTo("TX-123");
        assertThat(webhook.amountMinor()).isEqualTo(12550L);
    }

    @Test
    void rejectsFractionalOrOverflowingWebhookMinorAmounts() throws Exception {
        RestTemplate restTemplate = new RestTemplate();
        PaystackPaymentService service = service(restTemplate);
        byte[] body = "{\"event\":\"refund.processed\",\"data\":{\"transaction_reference\":\"TX-123\",\"refund_amount\":\"1.5\"}}"
                .getBytes(StandardCharsets.UTF_8);
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signature = new Hmac(WEBHOOK_SECRET).hex((timestamp + ".").getBytes(StandardCharsets.UTF_8), body);

        assertThatThrownBy(() -> service.parseAndVerifyWebhook(body,
                "t=" + timestamp + ",v1=whk_test:" + signature.substring(signature.indexOf('.') + 1), null))
                .hasMessageContaining("integer minor-unit");
    }

    @Test
    void treatsIncompleteSuccessfulHttpInitializationAsUnknown() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        PaystackPaymentService service = service(restTemplate);
        server.expect(requestTo("https://api.paystack.co/transaction/initialize"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> service.initialize("LF-456", new BigDecimal("10.00"), "KES",
                "buyer@example.com", "card", "payment-id", "CHK-456"))
                .isInstanceOf(PaystackPaymentService.ProviderOutcomeUnknownException.class);
        server.verify();
    }

    @Test
    void defaultsKenyanMobileMoneyRecipientBankCodeToMpesa() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        PaystackPaymentService service = service(restTemplate);
        server.expect(requestTo("https://api.paystack.co/transferrecipient"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"type":"mobile_money","name":"Recipient","account_number":"254700000001",
                         "currency":"KES","bank_code":"MPESA"}
                        """))
                .andRespond(withSuccess("""
                        {"status":true,"data":{"recipient_code":"RCP_test"}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(service.createRecipient(com.example.distrobackend.Domain.enums.PayoutMethod.MOBILE_MONEY,
                "Recipient", "254700000001", null, "KES", null).recipientCode()).isEqualTo("RCP_test");
        server.verify();
    }

    @Test
    void listsOnlyActiveKesBankDestinations() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        PaystackPaymentService service = service(restTemplate);
        server.expect(requestTo("https://api.paystack.co/bank?currency=KES"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer sk_test_secret"))
                .andRespond(withSuccess("""
                        {"status":true,"data":[
                          {"name":"Equity Bank","code":"68","active":true},
                          {"name":"Closed Bank","code":"99","active":false}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(service.listBankDestinations("kes"))
                .containsExactly(new PaystackPaymentService.DestinationOption(
                        "Equity Bank", "68", "KES", "bank", true));
        server.verify();
    }

    @Test
    void listsKenyanMobileMoneyProvidersIncludingMpesa() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        PaystackPaymentService service = service(restTemplate);
        server.expect(requestTo("https://api.paystack.co/bank?currency=KES&type=mobile_money"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"status":true,"data":[
                          {"name":"Airtel Kenya","code":"ATL_KE","active":true},
                          {"name":"M-PESA","code":"MPESA","active":true}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(service.listMobileMoneyDestinations("KES"))
                .extracting(PaystackPaymentService.DestinationOption::code)
                .containsExactly("ATL_KE", "MPESA");
        server.verify();
    }

    @Test
    void resolvesKesBankAccountNameUsingSelectedBankCode() {
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(restTemplate).build();
        PaystackPaymentService service = service(restTemplate);
        server.expect(requestTo("https://api.paystack.co/bank/resolve?account_number=0123456789&bank_code=68"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        {"status":true,"data":{"account_number":"0123456789","account_name":"Jane Doe"}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(service.resolveBankAccount("0123456789", "68", "KES"))
                .isEqualTo(new PaystackPaymentService.ResolvedBankAccount("Jane Doe", "68", "KES"));
        server.verify();
    }

    private static PaystackPaymentService service(RestTemplate restTemplate) {
        return service(restTemplate, "TEST", "sk_test_secret", WEBHOOK_SECRET);
    }

    private static PaystackPaymentService service(RestTemplate restTemplate, String mode,
                                                  String secretKey, String webhookSecret) {
        return new PaystackPaymentService(restTemplate, new JsonMapper(), true, true, mode,
                "https://api.paystack.co", secretKey, webhookSecret, "");
    }

    private record Hmac(String secret) {
        String hex(byte[] prefix, byte[] body) throws Exception {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            mac.update(prefix);
            mac.update(body);
            return HexFormat.of().formatHex(mac.doFinal());
        }
    }
}
