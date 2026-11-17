package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.OrganizationNotificationPreferences;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.OrganizationNotificationPreferencesRequest;
import com.example.distrobackend.dto.OrganizationNotificationPreferencesResponse;
import com.example.distrobackend.repository.OrganizationNotificationPreferencesRepository;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

@Service
@RequiredArgsConstructor
public class OrganizationNotificationPreferencesService {
    @PersistenceContext
    private EntityManager entityManager;

    private final OrganizationNotificationPreferencesRepository preferencesRepository;
    private final OrganizationRepository organizationRepository;

    @Transactional(readOnly = true)
    public OrganizationNotificationPreferencesResponse get(AuthenticatedUser actor) {
        var organization = requireOrganization(actor);
        return preferencesRepository.findById(organization.getId())
                .map(OrganizationNotificationPreferencesResponse::from)
                .orElseGet(() -> new OrganizationNotificationPreferencesResponse(
                        organization.getId(), false, false, false, null));
    }

    @Transactional
    public OrganizationNotificationPreferencesResponse update(
            AuthenticatedUser actor, OrganizationNotificationPreferencesRequest request) {
        var organization = requireOrganization(actor);
        if (request.dispatchExceptionsSms() == null
                && request.purchaseOrderApprovalsEmail() == null
                && request.weeklyCapacityForecasts() == null) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "At least one preference must be provided");
        }
        var existing = preferencesRepository.findById(organization.getId());
        boolean isNew = existing.isEmpty();
        OrganizationNotificationPreferences preferences = existing.orElseGet(() -> {
                    OrganizationNotificationPreferences created = new OrganizationNotificationPreferences();
                    created.setOrganization(organization);
                    return created;
                });
        if (request.dispatchExceptionsSms() != null) {
            preferences.setDispatchExceptionsSms(request.dispatchExceptionsSms());
        }
        if (request.purchaseOrderApprovalsEmail() != null) {
            preferences.setPurchaseOrderApprovalsEmail(request.purchaseOrderApprovalsEmail());
        }
        if (request.weeklyCapacityForecasts() != null) {
            preferences.setWeeklyCapacityForecasts(request.weeklyCapacityForecasts());
        }
        if (isNew) {
            entityManager.persist(preferences);
        }
        entityManager.flush();
        return OrganizationNotificationPreferencesResponse.from(preferences);
    }

    private com.example.distrobackend.Domain.entity.Organization requireOrganization(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null
                || (actor.role() != com.example.distrobackend.Domain.enums.UserRole.MANUFACTURER_ADMIN
                && actor.role() != com.example.distrobackend.Domain.enums.UserRole.MANUFACTURER_STAFF)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return organizationRepository.findById(actor.organizationId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Organization not found"));
    }
}
