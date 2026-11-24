package com.example.distrobackend.service;

import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MpesaCallbackGuardTest {

    private MpesaCallbackGuard guard;

    @BeforeEach
    void setUp() {
        guard = new MpesaCallbackGuard();
        ReflectionTestUtils.setField(guard, "callbackSecret", "callback-secret");
        ReflectionTestUtils.setField(guard, "allowedIps", "");
        ReflectionTestUtils.setField(guard, "allowlistRequired", false);
        ReflectionTestUtils.setField(guard, "verifyCallback", true);
        ReflectionTestUtils.setField(guard, "trustedProxyCount", 0);
        ReflectionTestUtils.setField(guard, "headerSecretEnabled", true);
    }

    @Test
    void urlTokenIsAcceptedWithoutACustomHeader() {
        guard.verifyCustomerCallback(null, "callback-secret", "127.0.0.1");
    }

    @Test
    void legacyHeaderIsStillAccepted() {
        guard.verifyCustomerCallback("callback-secret", null, null);
    }

    @Test
    void missingCredentialIsRejected() {
        assertThatThrownBy(() -> guard.verifyCustomerCallback(null, null, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void addressOutsideTheAllowlistIsRejected() {
        ReflectionTestUtils.setField(guard, "allowedIps", "203.0.113.10, 203.0.113.11");

        assertThatThrownBy(() -> guard.verifyCustomerCallback(null, "callback-secret", "198.51.100.4"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid M-Pesa callback credentials");
    }

    @Test
    void emptyAllowlistFailsClosedWhenRequired() {
        ReflectionTestUtils.setField(guard, "allowlistRequired", true);

        assertThatThrownBy(() -> guard.verifyCustomerCallback(null, "callback-secret", "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("allowlist");
    }

    @Test
    void callbackUrlReceivesTheTokenOnce() {
        String registered = guard.callbackUrlWithToken("https://api.example/api/v1/payments/mpesa/callback");

        assertThat(registered).isEqualTo(
                "https://api.example/api/v1/payments/mpesa/callback?token=callback-secret");
        assertThat(guard.callbackUrlWithToken(registered)).isEqualTo(registered);
    }

    @Test
    void socketAddressIsUsedWhenNoProxyIsTrusted() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Forwarded-For", "203.0.113.10");

        assertThat(guard.clientAddress(request)).isEqualTo("10.0.0.8");
    }

    @Test
    void clientAddressIsTheEntryAppendedByTheTrustedProxy() {
        ReflectionTestUtils.setField(guard, "trustedProxyCount", 1);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        request.addHeader("X-Forwarded-For", "198.51.100.1, 203.0.113.10");

        assertThat(guard.clientAddress(request)).isEqualTo("203.0.113.10");

        ReflectionTestUtils.setField(guard, "trustedProxyCount", 2);
        request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "198.51.100.1, 203.0.113.10, 10.0.0.1");
        assertThat(guard.clientAddress(request)).isEqualTo("203.0.113.10");
    }

    @Test
    void forgedForwardedForWithAnAllowedAddressIsStillRejected() {
        ReflectionTestUtils.setField(guard, "trustedProxyCount", 1);
        ReflectionTestUtils.setField(guard, "allowedIps", "196.201.214.200");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.8");
        // The attacker sends the allowed address; Render appends the attacker's real address.
        request.addHeader("X-Forwarded-For", "196.201.214.200, 198.51.100.66");

        String client = guard.clientAddress(request);

        assertThat(client).isEqualTo("198.51.100.66");
        assertThatThrownBy(() -> guard.verifyCustomerCallback(null, "callback-secret", client))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
    }

    @Test
    void tooFewForwardedEntriesYieldNoAddress() {
        ReflectionTestUtils.setField(guard, "trustedProxyCount", 2);
        ReflectionTestUtils.setField(guard, "allowedIps", "196.201.214.200");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "196.201.214.200");

        assertThat(guard.clientAddress(request)).isNull();
        assertThatThrownBy(() -> guard.verifyIp(guard.clientAddress(request)))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void headerSecretIsIgnoredWhenDisabled() {
        ReflectionTestUtils.setField(guard, "headerSecretEnabled", false);

        assertThatThrownBy(() -> guard.verifyCustomerCallback("callback-secret", null, "127.0.0.1"))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.UNAUTHENTICATED);
        assertThat(guard.headerSecretIfEnabled("callback-secret")).isNull();
        guard.verifyCustomerCallback("wrong-but-ignored", "callback-secret", "127.0.0.1");
    }

    @Test
    void tokenOfADifferentLengthIsRejected() {
        assertThatThrownBy(() -> guard.verifyCustomerCallback(null, "callback-secret-longer", "127.0.0.1"))
                .isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> guard.verifyCustomerCallback(null, "c", "127.0.0.1"))
                .isInstanceOf(ApiException.class);
    }
}
