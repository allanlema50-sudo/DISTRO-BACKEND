package com.example.distrobackend.dto;

/** Plain delivery code returned only to the customer who owns the order, or to demo dispatch. */
public record DeliveryOtpResponse(String code, long expiresInMinutes) {
}
