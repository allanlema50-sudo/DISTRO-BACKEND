package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.AccessRequest;
import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.AccessRequestStatus;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.dto.AccessRequestApprovalResponse;
import com.example.distrobackend.dto.AccessRequestCreateRequest;
import com.example.distrobackend.dto.AccessRequestResponse;
import com.example.distrobackend.dto.AccessRequestSubmissionResponse;
import com.example.distrobackend.event.OnboardingEmailRequested;
import com.example.distrobackend.repository.AccessRequestRepository;
import org.springframework.mock.web.MockMultipartFile;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessRequestServiceTest {

    @Mock private AccessRequestRepository accessRequestRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private NotificationService notificationService;
    @Mock private AccessRequestDocumentService accessRequestDocumentService;
    @Mock private AccessRequestSubmissionLimiter accessRequestSubmissionLimiter;

    private AccessRequestService accessRequestService;

    @BeforeEach
    void setUp() {
        accessRequestService = new AccessRequestService(
                accessRequestRepository,
                organizationRepository,
                userRepository,
                passwordEncoder,
                eventPublisher,
                notificationService,
                accessRequestDocumentService,
                accessRequestSubmissionLimiter);
        ReflectionTestUtils.setField(accessRequestService, "frontendBaseUrl", "https://app.example.test/");
        ReflectionTestUtils.setField(accessRequestService, "activationTtl", Duration.ofHours(24));
    }

    @Test
    void submittingRequestNotifiesEverySuperAdminInAppAndByEmail() {
        User firstAdmin = superAdmin("first-admin@example.test");
        User secondAdmin = superAdmin("second-admin@example.test");
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByPhoneNumber(any())).thenReturn(false);
        when(accessRequestRepository.existsByPersonalEmailAndStatus(any(), any())).thenReturn(false);
        when(accessRequestRepository.existsByPhoneNumberAndStatusIn(any(), any())).thenReturn(false);
        when(userRepository.findByRole(UserRole.SUPER_ADMIN)).thenReturn(List.of(firstAdmin, secondAdmin));
        when(accessRequestRepository.save(any(AccessRequest.class))).thenAnswer(invocation -> {
            AccessRequest request = invocation.getArgument(0);
            request.setId(UUID.randomUUID());
            return request;
        });

        AccessRequestSubmissionResponse response = accessRequestService.submit(request(
                        Organizationtype.MANUFACTURER, "amina@example.test", "admin@example-manufacturer.test"),
                validPdf("incorporation.pdf"), validPng("tax.png"));

        assertThat(response.status()).isEqualTo(AccessRequestStatus.PENDING);
        verify(notificationService, org.mockito.Mockito.times(2)).create(
                any(User.class), any(), any(), any(), any(), any(), any());
        ArgumentCaptor<OnboardingEmailRequested> emails =
                ArgumentCaptor.forClass(OnboardingEmailRequested.class);
        verify(eventPublisher, org.mockito.Mockito.times(2)).publishEvent(emails.capture());
        assertThat(emails.getAllValues())
                .extracting(OnboardingEmailRequested::recipient)
                .containsExactlyInAnyOrder("first-admin@example.test", "second-admin@example.test");
    }

    @Test
    void submittingRequestIsPersistedBeforeSuperAdminBootstrap() {
        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(userRepository.existsByPhoneNumber(any())).thenReturn(false);
        when(accessRequestRepository.existsByPersonalEmailAndStatus(any(), any())).thenReturn(false);
        when(accessRequestRepository.existsByPhoneNumberAndStatusIn(any(), any())).thenReturn(false);
        when(userRepository.findByRole(UserRole.SUPER_ADMIN)).thenReturn(List.of());
        when(accessRequestRepository.save(any(AccessRequest.class))).thenAnswer(invocation -> {
            AccessRequest request = invocation.getArgument(0);
            request.setId(UUID.randomUUID());
            return request;
        });

        AccessRequestSubmissionResponse response = accessRequestService.submit(request(
                        Organizationtype.DISTRIBUTOR, "brian@example.test", "admin@example-distributor.test"),
                validPdf("incorporation.pdf"), validPng("tax.png"));

        assertThat(response.status()).isEqualTo(AccessRequestStatus.PENDING);
        verify(accessRequestRepository).save(any(AccessRequest.class));
        verify(notificationService, never()).create(any(), any(), any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void approvalEmailsCompatibleLinkWithoutReturningRawActivationToken() {
        UUID requestId = UUID.randomUUID();
        UUID reviewerId = UUID.randomUUID();
        AccessRequest request = pendingRequest(requestId);
        Organization organization = new Organization();
        organization.setId(UUID.randomUUID());
        when(accessRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(userRepository.existsByEmail(request.getPersonalEmail())).thenReturn(false);
        when(userRepository.existsByPhoneNumber(request.getPhoneNumber())).thenReturn(false);
        when(organizationRepository.findByEmailIgnoreCase(request.getOrganizationEmail()))
                .thenReturn(Optional.empty());
        when(organizationRepository.save(any(Organization.class))).thenReturn(organization);
        when(userRepository.getReferenceById(reviewerId)).thenReturn(superAdmin("owner@example.test"));
        when(accessRequestRepository.save(any(AccessRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AccessRequestApprovalResponse response = accessRequestService.approve(
                requestId, principal(reviewerId));

        assertThat(response.request().status()).isEqualTo(AccessRequestStatus.APPROVED);
        assertThat(response.activationLink()).isNull();
        ArgumentCaptor<OnboardingEmailRequested> email =
                ArgumentCaptor.forClass(OnboardingEmailRequested.class);
        verify(eventPublisher).publishEvent(email.capture());
        assertThat(email.getValue().body())
                .contains("https://app.example.test/auth/activate?email=amina%40example.test&token=");
    }

    @Test
    void repeatingApprovalReturnsExistingDecisionWithoutCreatingAnotherOrganizationOrEmail() {
        UUID requestId = UUID.randomUUID();
        AccessRequest request = pendingRequest(requestId);
        request.setStatus(AccessRequestStatus.APPROVED);
        when(accessRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

        AccessRequestApprovalResponse response = accessRequestService.approve(
                requestId, principal(UUID.randomUUID()));

        assertThat(response.request().status()).isEqualTo(AccessRequestStatus.APPROVED);
        assertThat(response.activationLink()).isNull();
        verify(organizationRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void superAdminCanResendActivationWithAReplacementToken() {
        UUID requestId = UUID.randomUUID();
        AccessRequest request = pendingRequest(requestId);
        request.setStatus(AccessRequestStatus.APPROVED);
        request.setActivationTokenHash("old-token-hash");
        when(accessRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));
        when(accessRequestRepository.save(any(AccessRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AccessRequestResponse response = accessRequestService.resendActivation(
                requestId, principal(UUID.randomUUID()));

        assertThat(response.status()).isEqualTo(AccessRequestStatus.APPROVED);
        assertThat(request.getActivationTokenHash()).isNotEqualTo("old-token-hash");
        assertThat(request.getActivationExpiresAt()).isNotNull();
        verify(accessRequestRepository).save(request);
        verify(eventPublisher).publishEvent(any(OnboardingEmailRequested.class));
    }

    @Test
    void repeatingRejectionReturnsExistingDecisionWithoutMutatingIt() {
        UUID requestId = UUID.randomUUID();
        AccessRequest request = pendingRequest(requestId);
        request.setStatus(AccessRequestStatus.REJECTED);
        when(accessRequestRepository.findByIdForUpdate(requestId)).thenReturn(Optional.of(request));

        AccessRequestResponse response = accessRequestService.reject(
                requestId, principal(UUID.randomUUID()));

        assertThat(response.status()).isEqualTo(AccessRequestStatus.REJECTED);
        verify(accessRequestRepository, never()).save(any());
    }

    private AccessRequest pendingRequest(UUID id) {
        AccessRequest request = new AccessRequest();
        request.setId(id);
        request.setFullName("Amina Owner");
        request.setPersonalEmail("amina@example.test");
        request.setOrganizationEmail("admin@example-manufacturer.test");
        request.setPhoneNumber("+254712345678");
        request.setOrganizationName("Example Manufacturer");
        request.setOrganizationType(Organizationtype.MANUFACTURER);
        request.setStatus(AccessRequestStatus.PENDING);
        return request;
    }

    private AccessRequestCreateRequest request(Organizationtype type, String personalEmail,
                                               String organizationEmail) {
        return new AccessRequestCreateRequest(
                type,
                type == Organizationtype.MANUFACTURER ? "Example Manufacturer" : "Example Distributor",
                "Kenya",
                "CPR-123456",
                "P123456789Z",
                type == Organizationtype.MANUFACTURER ? "Amina Owner" : "Brian Owner",
                "Manager",
                personalEmail,
                organizationEmail,
                type == Organizationtype.MANUFACTURER ? "+254712345678" : "+254712345679",
                true);
    }

    private MockMultipartFile validPdf(String filename) {
        return new MockMultipartFile("incorporationCertificate", filename, "application/pdf",
                "%PDF-1.7\n".getBytes());
    }

    private MockMultipartFile validPng(String filename) {
        return new MockMultipartFile("taxCertificate", filename, "image/png",
                new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
    }

    private User superAdmin(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setRole(UserRole.SUPER_ADMIN);
        return user;
    }

    private AuthenticatedUser principal(UUID userId) {
        return new AuthenticatedUser(userId, UserRole.SUPER_ADMIN, null, null,
                Instant.now().plusSeconds(300));
    }
}
