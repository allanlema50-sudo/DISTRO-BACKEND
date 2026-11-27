package com.example.distrobackend.service;

import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.repository.AdminPaymentActivityRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminPaymentActivityServiceTest {
    @Mock
    private AdminPaymentActivityRepository activityRepository;

    @InjectMocks
    private AdminPaymentActivityService service;

    @Test
    void rejectsNonSuperAdminBeforeQuerying() {
        AuthenticatedUser actor = new AuthenticatedUser(UUID.randomUUID(), UserRole.CUSTOMER,
                null, null, Instant.now().plusSeconds(300));

        assertThrows(ApiException.class, () -> service.search(actor, null, null,
                null, null, null, null, null, 0, 50));
        verifyNoInteractions(activityRepository);
    }

    @Test
    void rejectsRangesLongerThanThirtyOneDays() {
        AuthenticatedUser actor = superAdmin();
        OffsetDateTime from = OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime to = from.plusDays(32);

        assertThrows(ApiException.class, () -> service.search(actor, from, to,
                null, null, null, null, null, 0, 50));
        verifyNoInteractions(activityRepository);
    }

    @Test
    void normalizesFiltersAndUsesBoundedPaging() {
        AuthenticatedUser actor = superAdmin();
        when(activityRepository.search(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq("PAYSTACK"),
                org.mockito.ArgumentMatchers.eq("TEST"), org.mockito.ArgumentMatchers.eq("SUCCESS"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq("checkout-1"),
                org.mockito.ArgumentMatchers.any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(2, 25), 0));

        service.search(actor, null, null, " paystack ", "test", "success",
                null, " checkout-1 ", 2, 25);

        ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(activityRepository).search(org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq("PAYSTACK"),
                org.mockito.ArgumentMatchers.eq("TEST"), org.mockito.ArgumentMatchers.eq("SUCCESS"),
                org.mockito.ArgumentMatchers.isNull(), org.mockito.ArgumentMatchers.eq("checkout-1"),
                pageCaptor.capture());
        assertEquals(PageRequest.of(2, 25), pageCaptor.getValue());
    }

    @Test
    void rejectsOversizedPage() {
        assertThrows(ApiException.class, () -> service.search(superAdmin(), null, null,
                null, null, null, null, null, 0, 101));
        verifyNoInteractions(activityRepository);
    }

    private static AuthenticatedUser superAdmin() {
        return new AuthenticatedUser(UUID.randomUUID(), UserRole.SUPER_ADMIN,
                null, null, Instant.now().plusSeconds(300));
    }
}
