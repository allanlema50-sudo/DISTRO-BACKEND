package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.OrganizationPayout;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.RecordOrganizationPayoutRequest;
import com.example.distrobackend.repository.OrganizationPayoutRepository;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationPayoutServiceTest {

    @Mock private OrganizationPayoutRepository payoutRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private UserRepository userRepository;
    @Mock private FinancialLedgerService ledger;

    private OrganizationPayoutService service;
    private Organization distributor;
    private AuthenticatedUser superAdmin;

    @BeforeEach
    void setUp() {
        service = new OrganizationPayoutService(payoutRepository, organizationRepository, userRepository, ledger);
        distributor = new Organization();
        distributor.setId(UUID.randomUUID());
        superAdmin = new AuthenticatedUser(UUID.randomUUID(), UserRole.SUPER_ADMIN, null, null,
                Instant.now().plusSeconds(600));
        when(payoutRepository.findByIdempotencyKey(any())).thenReturn(Optional.empty());
        when(organizationRepository.findByIdForUpdate(distributor.getId())).thenReturn(Optional.of(distributor));
    }

    @Test
    void payoutIsBlockedWhileReversalsLeaveTheBalanceNegative() {
        // Paid out 1000, then a 1300 capture was reversed: the organization owes 300.
        when(ledger.payableBalance(distributor.getId(), "KES")).thenReturn(new BigDecimal("-300.00"));

        assertThatThrownBy(() -> service.record(superAdmin, request("1.00"), "payout-1"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("owes 300.00 KES")
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(payoutRepository, never()).saveAndFlush(any());
        verify(ledger, never()).recordOrganizationPayout(any(), any(), any(), any(), any());
    }

    @Test
    void laterCapturesNetAgainstTheDeficitBeforeAnyPayout() {
        // A new 500 capture after the -300 deficit leaves 200 available.
        when(ledger.payableBalance(distributor.getId(), "KES")).thenReturn(new BigDecimal("200.00"));
        when(payoutRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            OrganizationPayout payout = invocation.getArgument(0);
            payout.setId(UUID.randomUUID());
            return payout;
        });

        assertThatThrownBy(() -> service.record(superAdmin, request("200.01"), "payout-2"))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("exceeds the organization payable balance");

        assertThat(service.record(superAdmin, request("200.00"), "payout-3").amount())
                .isEqualByComparingTo("200.00");
        verify(ledger).recordOrganizationPayout(any(), eq(distributor), eq(new BigDecimal("200.00")),
                eq("KES"), eq("FT-1"));
    }

    private RecordOrganizationPayoutRequest request(String amount) {
        return new RecordOrganizationPayoutRequest(distributor.getId(), new BigDecimal(amount), "KES", "FT-1", null);
    }
}
