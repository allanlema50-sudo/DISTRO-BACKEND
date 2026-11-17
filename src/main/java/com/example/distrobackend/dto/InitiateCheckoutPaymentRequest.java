package com.example.distrobackend.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record InitiateCheckoutPaymentRequest(@NotBlank String method, @Size(max=120) String idempotencyKey,
                                             @jakarta.validation.constraints.Pattern(regexp="^254[17][0-9]{8}$", message="Use a Kenyan number in 254XXXXXXXXX format") String phoneNumber) {
 public InitiateCheckoutPaymentRequest(String method, String idempotencyKey) { this(method, idempotencyKey, null); }
}
