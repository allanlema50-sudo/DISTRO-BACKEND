package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.AuditLog;
import com.example.distrobackend.Domain.entity.CommissionRule;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.CommissionRuleResponse;
import com.example.distrobackend.dto.CreateCommissionRuleRequest;
import com.example.distrobackend.repository.AuditLogRepository;
import com.example.distrobackend.repository.CommissionRuleRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CommissionRuleService {
    private final CommissionRuleRepository commissionRuleRepository;
    private final UserRepository userRepository;
    private final AuditLogRepository auditLogRepository;

    @Value("${logiflow.commission.default-percent:5.00}")
    private BigDecimal defaultRatePercent;

    @PostConstruct
    void validateDefaultRate() {
        if (defaultRatePercent == null || defaultRatePercent.signum() < 0
                || defaultRatePercent.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new IllegalStateException("LOGIFLOW_DEFAULT_COMMISSION_PERCENT must be between 0 and 100");
        }
    }

    @Transactional(readOnly = true)
    public List<CommissionRuleResponse> list(AuthenticatedUser actor) {
        requirePlatformAdmin(actor);
        return commissionRuleRepository.findAllByOrderByCurrencyAscEffectiveFromDesc()
                .stream().map(CommissionRuleResponse::from).toList();
    }

    @Transactional
    public CommissionRuleResponse create(AuthenticatedUser actor, CreateCommissionRuleRequest request) {
        requirePlatformAdmin(actor);
        String currency = normalizeCurrency(request.currency());
        validateWindow(request.effectiveFrom(), request.effectiveUntil());
        List<CommissionRule> existingRules = commissionRuleRepository.findActiveForUpdate(currency);
        if (existingRules.stream().anyMatch(existing -> overlaps(existing, request, currency))) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "An active commission rule already covers the requested currency, organization type and period");
        }

        CommissionRule rule = new CommissionRule();
        rule.setRatePercent(request.ratePercent().setScale(4, RoundingMode.HALF_UP));
        rule.setFixedFee(request.fixedFee().setScale(2, RoundingMode.HALF_UP));
        rule.setCurrency(currency);
        rule.setOrganizationType(request.organizationType());
        rule.setEffectiveFrom(request.effectiveFrom());
        rule.setEffectiveUntil(request.effectiveUntil());
        rule.setCreatedBy(userRepository.getReferenceById(actor.userId()));
        CommissionRule saved;
        try {
            saved = commissionRuleRepository.saveAndFlush(rule);
        } catch (DataIntegrityViolationException ex) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "An active commission rule already covers the requested currency, organization type and period");
        }
        audit(actor, "COMMISSION_RULE_CREATED", saved.getId(), Map.of(
                "currency", currency,
                "ratePercent", saved.getRatePercent().toPlainString(),
                "organizationType", saved.getOrganizationType() == null ? "GLOBAL" : saved.getOrganizationType().name()));
        return CommissionRuleResponse.from(saved);
    }

    @Transactional
    public CommissionRuleResponse deactivate(AuthenticatedUser actor, UUID ruleId) {
        requirePlatformAdmin(actor);
        CommissionRule rule = commissionRuleRepository.findById(ruleId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Commission rule not found"));
        if (!rule.isActive()) return CommissionRuleResponse.from(rule);
        rule.setActive(false);
        CommissionRule saved = commissionRuleRepository.saveAndFlush(rule);
        audit(actor, "COMMISSION_RULE_DEACTIVATED", saved.getId(), Map.of("currency", saved.getCurrency()));
        return CommissionRuleResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public CommissionDecision resolve(Organizationtype organizationType, String currency, OffsetDateTime at) {
        String normalizedCurrency = normalizeCurrency(currency);
        return commissionRuleRepository.findActive(normalizedCurrency).stream()
                .filter(rule -> !rule.getEffectiveFrom().isAfter(at))
                .filter(rule -> rule.getEffectiveUntil() == null || at.isBefore(rule.getEffectiveUntil()))
                .filter(rule -> rule.getOrganizationType() == null || rule.getOrganizationType() == organizationType)
                .sorted(Comparator
                        .comparing((CommissionRule rule) -> rule.getOrganizationType() == organizationType)
                        .reversed()
                        .thenComparing(CommissionRule::getEffectiveFrom, Comparator.reverseOrder()))
                .findFirst()
                .map(rule -> new CommissionDecision(rule.getId(), rule.getRatePercent(), rule.getFixedFee(), normalizedCurrency))
                .orElseGet(() -> new CommissionDecision(null, defaultRatePercent, BigDecimal.ZERO, normalizedCurrency));
    }

    private boolean overlaps(CommissionRule existing, CreateCommissionRuleRequest request, String currency) {
        return existing.isActive()
                && existing.getCurrency().equals(currency)
                && Objects.equals(existing.getOrganizationType(), request.organizationType())
                && existing.getEffectiveFrom().isBefore(request.effectiveUntil() == null
                ? OffsetDateTime.MAX : request.effectiveUntil())
                && request.effectiveFrom().isBefore(existing.getEffectiveUntil() == null
                ? OffsetDateTime.MAX : existing.getEffectiveUntil());
    }

    private static void validateWindow(OffsetDateTime from, OffsetDateTime until) {
        if (until != null && !from.isBefore(until)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "effectiveUntil must be after effectiveFrom");
        }
    }

    private static String normalizeCurrency(String currency) {
        String normalized = currency == null ? "" : currency.trim().toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z]{3}")) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "currency must be a three-letter ISO currency code");
        }
        return normalized;
    }

    private void audit(AuthenticatedUser actor, String action, UUID entityId, Map<String, Object> metadata) {
        AuditLog log = new AuditLog();
        log.setActor(userRepository.getReferenceById(actor.userId()));
        log.setAction(action);
        log.setEntityType("COMMISSION_RULE");
        log.setEntityId(entityId);
        log.setMetadata(metadata);
        auditLogRepository.save(log);
    }

    private static void requirePlatformAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }

    public record CommissionDecision(UUID ruleId, BigDecimal ratePercent, BigDecimal fixedFee, String currency) {
        public FinancialLedgerService.FinancialBreakdown calculate(BigDecimal gross, BigDecimal providerFee) {
            if (gross == null || providerFee == null || gross.signum() <= 0 || providerFee.signum() < 0) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Payment amounts must be valid positive values");
            }
            BigDecimal maximumCommission = gross.subtract(providerFee).setScale(2, RoundingMode.HALF_UP);
            if (maximumCommission.signum() < 0) {
                throw new ApiException(ErrorCode.CONFLICT, "Provider fees exceed the payment amount");
            }
            BigDecimal calculatedCommission = gross.multiply(ratePercent)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    .add(fixedFee)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal commission = calculatedCommission.min(maximumCommission);
            BigDecimal payable = gross.subtract(providerFee).subtract(commission).setScale(2, RoundingMode.HALF_UP);
            return new FinancialLedgerService.FinancialBreakdown(gross, providerFee, commission, payable, currency, ruleId);
        }
    }
}
