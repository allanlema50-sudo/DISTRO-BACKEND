package com.example.distrobackend.dto.mpesa;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class MpesaStkPushRequest {
    @JsonProperty("BusinessShortCode")
    private String businessShortCode;

    @JsonProperty("Password")
    private String password;

    @JsonProperty("Timestamp")
    private String timestamp;

    @JsonProperty("TransactionType")
    private String transactionType; // e.g., "CustomerPayBillOnline"

    @JsonProperty("Amount")
    private String amount;

    @JsonProperty("PartyA")
    private String partyA; // Phone number sending the money

    @JsonProperty("PartyB")
    private String partyB; // Shortcode receiving the money

    @JsonProperty("PhoneNumber")
    private String phoneNumber; // Phone number receiving the STK push prompt

    @JsonProperty("CallBackURL")
    private String callBackURL;

    @JsonProperty("AccountReference")
    private String accountReference; // Order ID or string

    @JsonProperty("TransactionDesc")
    private String transactionDesc;
}
