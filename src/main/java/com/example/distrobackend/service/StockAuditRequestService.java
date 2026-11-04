package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.StockAuditRequest;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.StockAuditRequestCreate;
import com.example.distrobackend.dto.StockAuditRequestResponse;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.StockAuditRequestRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StockAuditRequestService {
    private final StockAuditRequestRepository auditRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    @Transactional
    public StockAuditRequestResponse request(AuthenticatedUser actor, StockAuditRequestCreate request) {
        if (actor == null || actor.organizationId() == null
                || (actor.role() != UserRole.MANUFACTURER_ADMIN
                && actor.role() != UserRole.MANUFACTURER_STAFF)
                || actor.organizationType() != Organizationtype.MANUFACTURER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        if (!"ORGANIZATION".equalsIgnoreCase(request.scope().trim())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "scope must be ORGANIZATION");
        }
        StockAuditRequest audit = new StockAuditRequest();
        audit.setOrganization(organizationRepository.getReferenceById(actor.organizationId()));
        audit.setRequestedBy(userRepository.getReferenceById(actor.userId()));
        audit.setScope("ORGANIZATION");
        audit.setTerminalId(request.terminalId().trim());
        return StockAuditRequestResponse.from(auditRepository.saveAndFlush(audit));
    }
}
