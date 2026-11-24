package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.OrganizationPayout;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.OrganizationPayoutResponse;
import com.example.distrobackend.dto.RecordOrganizationPayoutRequest;
import com.example.distrobackend.repository.OrganizationPayoutRepository;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class OrganizationPayoutService {

    private final OrganizationPayoutRepository payoutRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final FinancialLedgerService financialLedgerService;

    @Transactional
    public OrganizationPayoutResponse record(AuthenticatedUser actor, RecordOrganizationPayoutRequest request,
                                             String idempotencyHeader) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        String key = clean(idempotencyHeader) != null ? clean(idempotencyHeader) : clean(request.idempotencyKey());
        if (key == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "An idempotency key is required");
        }
        OrganizationPayout existing = payoutRepository.findByIdempotencyKey(key).orElse(null);
        if (existing != null) {
            if (!existing.getOrganization().getId().equals(request.organizationId())
                    || existing.getAmount().compareTo(request.amount()) != 0
                    || !existing.getCurrency().equalsIgnoreCase(currency(request))
                    || !existing.getBankReference().equals(request.bankReference().trim())) {
                throw new ApiException(ErrorCode.CONFLICT, "Idempotency key has already been used");
            }
            return OrganizationPayoutResponse.from(existing);
        }
        String normalizedCurrency = currency(request);
        Organization organization = organizationRepository.findByIdForUpdate(request.organizationId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Organization not found"));
        BigDecimal balance = financialLedgerService.payableBalance(organization.getId(), normalizedCurrency);
        if (balance.signum() <= 0) {
            throw new ApiException(ErrorCode.CONFLICT, balance.signum() < 0
                    ? "Organization owes " + balance.negate().toPlainString() + " " + normalizedCurrency
                        + " from reversals after earlier payouts; new captures must clear it before a payout"
                    : "Organization has no payable balance");
        }
        if (balance.compareTo(request.amount()) < 0) {
            throw new ApiException(ErrorCode.CONFLICT, "Payout exceeds the organization payable balance");
        }
        User actorUser = userRepository.getReferenceById(actor.userId());
        OrganizationPayout payout = new OrganizationPayout();
        payout.setOrganization(organization);
        payout.setAmount(request.amount());
        payout.setCurrency(normalizedCurrency);
        payout.setBankReference(request.bankReference().trim());
        payout.setIdempotencyKey(key);
        payout.setCreatedBy(actorUser);
        OrganizationPayout saved = payoutRepository.saveAndFlush(payout);
        financialLedgerService.recordOrganizationPayout(saved.getId(), organization, request.amount(),
                normalizedCurrency, saved.getBankReference());
        return OrganizationPayoutResponse.from(saved);
    }

    private static String currency(RecordOrganizationPayoutRequest request) {
        String value = request.currency() == null || request.currency().isBlank()
                ? "KES" : request.currency().trim().toUpperCase(Locale.ROOT);
        if (!value.matches("[A-Z]{3}")) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "currency must be a three-letter ISO currency code");
        }
        return value;
    }

    private static String clean(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
