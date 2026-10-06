package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.AccessRequest;
import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.AccessRequestStatus;
import com.example.distrobackend.Domain.enums.NotificationPriority;
import com.example.distrobackend.Domain.enums.NotificationType;
import com.example.distrobackend.repository.AccessRequestRepository;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.dto.AccessRequestResponse;
import com.example.distrobackend.dto.CreateAccessRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AccessRequestService {

    private final AccessRequestRepository accessRequestRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final NotificationService notificationService;
    private final InvitationService invitationService;

    // ================================================================
    // CREATE ACCESS REQUEST
    // ================================================================

    @Transactional
    public AccessRequestResponse create(
            CreateAccessRequest request
    ) {

        AccessRequest accessRequest =
                new AccessRequest();

        accessRequest.setFullName(
                request.fullName()
        );

        accessRequest.setEmail(
                request.email()
        );

        accessRequest.setPhoneNumber(
                request.phoneNumber()
        );

        accessRequest.setOrganizationName(
                request.organizationName()
        );

        accessRequest.setOrganizationType(
                request.organizationType()
        );

        accessRequest.setRequestedRole(
                request.requestedRole()
        );

        accessRequest.setStatus(
                AccessRequestStatus.PENDING
        );

        AccessRequest saved =
                accessRequestRepository.save(
                        accessRequest
                );

        // ------------------------------------------------------------
        // NOTIFY PLATFORM ADMINS
        // ------------------------------------------------------------

        List<User> users =
                userRepository.findAll();

        for (User user : users) {

            if (user.getRole() != null
                    && "PLATFORM_ADMIN".equals(
                            user.getRole().name()
                    )) {

                notificationService.create(
                        user,
                        "New Organization Access Request",
                        request.fullName()
                                + " from "
                                + request.organizationName()
                                + " has requested access to the platform.",
                        NotificationType.ACCESS_REQUEST,
                        NotificationPriority.HIGH,
                        saved.getId(),
                        "ACCESS_REQUEST"
                );
            }
        }

        return AccessRequestResponse.from(
                saved
        );
    }

    // ================================================================
    // GET ALL
    // ================================================================

    @Transactional(readOnly = true)
    public List<AccessRequestResponse> getAll() {

        return accessRequestRepository
                .findAllByOrderByCreatedAtDesc()
                .stream()
                .map(AccessRequestResponse::from)
                .toList();
    }

    // ================================================================
    // GET ONE
    // ================================================================

    @Transactional(readOnly = true)
    public AccessRequestResponse getById(
            UUID id
    ) {

        AccessRequest request =
                accessRequestRepository
                        .findById(id)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Access request not found"
                                )
                        );

        return AccessRequestResponse.from(
                request
        );
    }

    // ================================================================
    // APPROVE
    // ================================================================

    @Transactional
    public AccessRequestResponse approve(
            UUID id,
            User reviewer
    ) {

        AccessRequest request =
                accessRequestRepository
                        .findById(id)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Access request not found"
                                )
                        );

        if (request.getStatus()
                != AccessRequestStatus.PENDING) {

            throw new IllegalStateException(
                    "Only pending requests can be approved"
            );
        }

        // ------------------------------------------------------------
        // CREATE ORGANIZATION
        // ------------------------------------------------------------

        Organization organization =
                new Organization();

        organization.setName(
                request.getOrganizationName()
        );

        organization.setType(
                request.getOrganizationType()
        );

        Organization savedOrganization =
                organizationRepository.save(
                        organization
                );

        // ------------------------------------------------------------
        // UPDATE REQUEST
        // ------------------------------------------------------------

        request.setStatus(
                AccessRequestStatus.APPROVED
        );

        request.setReviewedAt(
                OffsetDateTime.now()
        );

        request.setReviewedBy(
                reviewer
        );

        AccessRequest saved =
                accessRequestRepository.save(
                        request
                );

        // ------------------------------------------------------------
        // CREATE INVITATION + SEND EMAIL
        // ------------------------------------------------------------

        invitationService.createInvitationFromAccessRequest(
                saved,
                savedOrganization
        );

        return AccessRequestResponse.from(
                saved
        );
    }

    // ================================================================
    // REJECT
    // ================================================================

    @Transactional
    public AccessRequestResponse reject(
            UUID id,
            User reviewer
    ) {

        AccessRequest request =
                accessRequestRepository
                        .findById(id)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Access request not found"
                                )
                        );

        if (request.getStatus()
                != AccessRequestStatus.PENDING) {

            throw new IllegalStateException(
                    "Only pending requests can be rejected"
            );
        }

        request.setStatus(
                AccessRequestStatus.REJECTED
        );

        request.setReviewedAt(
                OffsetDateTime.now()
        );

        request.setReviewedBy(
                reviewer
        );

        AccessRequest saved =
                accessRequestRepository.save(
                        request
                );

        return AccessRequestResponse.from(
                saved
        );
    }
}