package com.example.distrobackend.service;

import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Safaricom posts the callback to the URL registered on the STK request and
 * cannot add a custom header. The shared secret travels in that URL as
 * {@code token}. Never log the token, the query string, or the registered URL.
 */
@Component
public class MpesaCallbackGuard {

    @Value("${mpesa.callback-secret:}")
    private String callbackSecret;

    @Value("${mpesa.callback-allowed-ips:}")
    private String allowedIps;

    @Value("${mpesa.callback-ip-allowlist-required:false}")
    private boolean allowlistRequired;

    @Value("${mpesa.verify-callback:true}")
    private boolean verifyCallback;

    /**
     * Proxies in front of the app that append to X-Forwarded-For. Render adds
     * one. Zero ignores the header and uses the socket address.
     */
    @Value("${mpesa.callback-trusted-proxy-count:0}")
    private int trustedProxyCount;

    @Value("${mpesa.callback-header-secret-enabled:true}")
    private boolean headerSecretEnabled;

    /**
     * Each trusted proxy appends the address it received the request from, so
     * the client is the entry {@code trustedProxyCount} places from the right.
     * Entries further left are client-controlled.
     */
    public String clientAddress(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        if (trustedProxyCount <= 0) {
            return request.getRemoteAddr();
        }
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded == null || forwarded.isBlank()) {
            return null;
        }
        List<String> hops = Arrays.stream(forwarded.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        if (hops.size() < trustedProxyCount) {
            return null;
        }
        return hops.get(hops.size() - trustedProxyCount);
    }

    /** Returns the header only when the legacy header path is enabled. */
    public String headerSecretIfEnabled(String headerSecret) {
        return headerSecretEnabled ? headerSecret : null;
    }

    public void verifyCustomerCallback(String headerSecret, String queryToken, String remoteAddress) {
        if (!verifyCallback) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED,
                    "M-Pesa callback verification must remain enabled");
        }
        if (isBlank(callbackSecret)) {
            throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED,
                    "M-Pesa callback secret is not configured");
        }
        String effectiveHeader = headerSecretIfEnabled(headerSecret);
        boolean headerMatches = matches(effectiveHeader);
        boolean tokenMatches = matches(queryToken);
        if (!headerMatches && !tokenMatches) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid M-Pesa callback credentials");
        }
        if (!isBlank(effectiveHeader) && !headerMatches) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid M-Pesa callback credentials");
        }
        if (!isBlank(queryToken) && !tokenMatches) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid M-Pesa callback credentials");
        }
        verifyIp(remoteAddress);
    }

    public void verifyIp(String remoteAddress) {
        Set<String> allowed = allowedAddresses();
        if (allowed.isEmpty()) {
            if (allowlistRequired) {
                throw new ApiException(ErrorCode.PAYMENT_NOT_CONFIGURED,
                        "M-Pesa callback IP allowlist is not configured");
            }
            return;
        }
        if (remoteAddress == null || !allowed.contains(remoteAddress.trim())) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED, "Invalid M-Pesa callback credentials");
        }
    }

    public String callbackUrlWithToken(String callbackUrl) {
        if (isBlank(callbackUrl) || isBlank(callbackSecret)) {
            return callbackUrl;
        }
        if (callbackUrl.contains("token=")) {
            return callbackUrl;
        }
        String separator = callbackUrl.contains("?") ? "&" : "?";
        return callbackUrl + separator + "token="
                + URLEncoder.encode(callbackSecret, StandardCharsets.UTF_8);
    }

    /** Hashing first keeps the comparison time independent of the supplied length. */
    private boolean matches(String supplied) {
        if (isBlank(supplied) || isBlank(callbackSecret)) {
            return false;
        }
        return MessageDigest.isEqual(sha256(callbackSecret), sha256(supplied));
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is unavailable", ex);
        }
    }

    private Set<String> allowedAddresses() {
        if (isBlank(allowedIps)) {
            return Set.of();
        }
        return Arrays.stream(allowedIps.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .collect(Collectors.toUnmodifiableSet());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
