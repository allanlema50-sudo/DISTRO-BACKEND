package com.example.distrobackend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
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
    void rejectsInvalidWebhookSignature() {
        PaystackPaymentService service = service(new RestTemplate());
        assertThatThrownBy(() -> service.parseAndVerifyWebhook(
                "{}".getBytes(StandardCharsets.UTF_8),
                "t=" + Instant.now().getEpochSecond() + ",v1=whk_test:00", null))
                .hasMessage("Invalid Paystack webhook signature");
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

    private static PaystackPaymentService service(RestTemplate restTemplate) {
        return new PaystackPaymentService(restTemplate, new ObjectMapper(), true, true, "TEST",
                "https://api.paystack.co", "sk_test_secret", WEBHOOK_SECRET, "");
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
