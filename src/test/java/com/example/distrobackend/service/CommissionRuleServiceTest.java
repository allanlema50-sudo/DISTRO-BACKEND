package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.CommissionRule;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.CreateCommissionRuleRequest;
import com.example.distrobackend.repository.AuditLogRepository;
import com.example.distrobackend.repository.CommissionRuleRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommissionRuleServiceTest {
    @Mock private CommissionRuleRepository ruleRepository;
    @Mock private UserRepository userRepository;
    @Mock private AuditLogRepository auditLogRepository;

    private CommissionRuleService service;
    private AuthenticatedUser admin;

    @BeforeEach
    void setUp() {
        service = new CommissionRuleService(ruleRepository, userRepository, auditLogRepository);
        ReflectionTestUtils.setField(service, "defaultRatePercent", new BigDecimal("5.00"));
        admin = new AuthenticatedUser(UUID.randomUUID(), UserRole.SUPER_ADMIN, null, null,
                java.time.Instant.now().plusSeconds(300));
    }

    @Test
    void resolvesTheMostSpecificEffectiveRule() {
        CommissionRule global = rule(new BigDecimal("5.00"), null,
                OffsetDateTime.now().minusDays(2));
        CommissionRule distributor = rule(new BigDecimal("7.50"), Organizationtype.DISTRIBUTOR,
                OffsetDateTime.now().minusDays(1));
        when(ruleRepository.findActive("KES")).thenReturn(List.of(global, distributor));

        var result = service.resolve(Organizationtype.DISTRIBUTOR, "kes", OffsetDateTime.now());

        assertThat(result.ratePercent()).isEqualByComparingTo("7.50");
        assertThat(result.currency()).isEqualTo("KES");
    }

    @Test
    void rejectsOverlappingRulesForTheSameScope() {
        CommissionRule existing = rule(new BigDecimal("5.00"), Organizationtype.DISTRIBUTOR,
                OffsetDateTime.now().minusDays(1));
        when(ruleRepository.findActiveForUpdate("KES")).thenReturn(List.of(existing));
        CreateCommissionRuleRequest request = new CreateCommissionRuleRequest(
                new BigDecimal("6.00"), BigDecimal.ZERO, "KES", Organizationtype.DISTRIBUTOR,
                OffsetDateTime.now().minusHours(1), null);

        assertThatThrownBy(() -> service.create(admin, request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("already covers");
    }

    private CommissionRule rule(BigDecimal rate, Organizationtype type, OffsetDateTime from) {
        CommissionRule rule = new CommissionRule();
        rule.setId(UUID.randomUUID());
        rule.setRatePercent(rate);
        rule.setFixedFee(BigDecimal.ZERO);
        rule.setCurrency("KES");
        rule.setOrganizationType(type);
        rule.setEffectiveFrom(from);
        rule.setActive(true);
        return rule;
    }
}
