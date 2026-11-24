package com.example.distrobackend.service;

import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.OrganizationLedgerBalanceResponse;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationLedgerQueryService {

    private final FinancialLedgerService financialLedgerService;

    @Transactional(readOnly = true)
    public OrganizationLedgerBalanceResponse balance(AuthenticatedUser actor, UUID organizationId, String currency) {
        if (actor == null || actor.role() == null) {
            throw new ApiException(ErrorCode.UNAUTHENTICATED);
        }
        UUID target = switch (actor.role()) {
            case SUPER_ADMIN -> {
                if (organizationId == null) {
                    throw new ApiException(ErrorCode.BAD_REQUEST, "organizationId is required");
                }
                yield organizationId;
            }
            case DISTRIBUTOR_ADMIN, DISTRIBUTOR_STAFF, MANUFACTURER_ADMIN, MANUFACTURER_STAFF -> {
                if (actor.organizationId() == null) {
                    throw new ApiException(ErrorCode.ACCESS_DENIED);
                }
                if (organizationId != null && !organizationId.equals(actor.organizationId())) {
                    throw new ApiException(ErrorCode.ACCESS_DENIED);
                }
                yield actor.organizationId();
            }
            default -> throw new ApiException(ErrorCode.ACCESS_DENIED);
        };
        return new OrganizationLedgerBalanceResponse(target, currency,
                financialLedgerService.payableBalance(target, currency));
    }
}
