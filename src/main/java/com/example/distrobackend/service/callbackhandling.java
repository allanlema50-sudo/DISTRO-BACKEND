package com.example.distrobackend.service;

import com.example.distrobackend.dto.mpesa.MpesaCallbackRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Adapts Daraja's public callback request to the payment state transition service. */
@Service
@RequiredArgsConstructor
public class callbackhandling {

    private static final String ACCEPTED_RESPONSE = "{\"ResultCode\":0,\"ResultDesc\":\"Accepted\"}";

    private final PaymentService paymentService;

    /**
     * Validates and applies the callback before acknowledging it to Daraja.
     * Exceptions intentionally propagate so rejected or unverifiable callbacks
     * are never acknowledged as successful.
     */
    public String handle(String callbackSecret, MpesaCallbackRequest callbackRequest) {
        paymentService.processMpesaCallback(callbackSecret, callbackRequest);
        return ACCEPTED_RESPONSE;
    }
}
