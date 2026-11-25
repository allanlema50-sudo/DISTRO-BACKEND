package com.example.distrobackend.service;

import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.mpesa.MpesaStkPushRequest;
import com.example.distrobackend.dto.mpesa.MpesaStkPushResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;

@Service
public class DarajaCustomerCollectionProvider implements CustomerCollectionProvider {

    private static final DateTimeFormatter DARAJA_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneId.of("Africa/Nairobi"));

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

    public DarajaCustomerCollectionProvider(@Qualifier("darajaRestTemplate") RestTemplate darajaRestTemplate) {
        this.darajaRestTemplate = darajaRestTemplate;
    }

    @Override
    public MpesaStkPushResponse push(String accountReference, String description, String phone,
                                     int amountKes, String callbackUrl) {
        String timestamp = DARAJA_TIMESTAMP.format(java.time.Instant.now());
        String password = Base64.getEncoder().encodeToString(
                (shortcode + passkey + timestamp).getBytes(StandardCharsets.UTF_8));
        MpesaStkPushRequest request = MpesaStkPushRequest.builder()
                .businessShortCode(shortcode)
                .password(password)
                .timestamp(timestamp)
                .transactionType("CustomerPayBillOnline")
                .amount(String.valueOf(amountKes))
                .partyA(phone.replace("+", ""))
                .partyB(shortcode)
                .phoneNumber(phone.replace("+", ""))
                .callBackURL(callbackUrl)
                .accountReference(accountReference)
                .transactionDesc(description)
                .build();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<MpesaStkPushResponse> response = darajaRestTemplate.postForEntity(
                baseUrl + "/mpesa/stkpush/v1/processrequest",
                new HttpEntity<>(request, headers), MpesaStkPushResponse.class);
        return response.getBody();
    }

    private String accessToken() {
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
}
