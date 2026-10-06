package com.example.distrobackend.dto.mpesa;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class MpesaStkPushResponse {
    @JsonProperty("MerchantRequestID")
    private String merchantRequestId;

    @JsonProperty("CheckoutRequestID")
    private String checkoutRequestId;

    @JsonProperty("ResponseCode")
    private String responseCode;

    @JsonProperty("ResponseDescription")
    private String responseDescription;

    @JsonProperty("CustomerMessage")
    private String customerMessage;
}
