package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.TripType;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.dto.TripLocation;
import com.example.distrobackend.dto.TripRequest;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.OrderRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.TripLocationPingRepository;
import com.example.distrobackend.repository.TripRepository;
import com.example.distrobackend.repository.TripStatusHistoryRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.security.TripAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TripServiceTest {

    @Mock private TripRepository tripRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private StockItemRepository stockItemRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private TripLocationPingRepository locationPingRepository;
    @Mock private TripStatusHistoryRepository tripStatusHistoryRepository;
    @Mock private TripAccessService tripAccessService;
    @Mock private OtpService otpService;
    @Mock private OrderService orderService;
    @Mock private SimpMessagingTemplate messagingTemplate;

    @InjectMocks private TripService tripService;

    @Test
    void tripStopsMustStartAtOneAndBeConsecutive() {
        UUID organizationId = UUID.randomUUID();
        Organization organization = new Organization();
        organization.setId(organizationId);
        organization.setType(Organizationtype.MANUFACTURER);
        when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization));

        TripLocation location = new TripLocation("Warehouse", "Main Road", 0.0, 0.0);
        TripRequest.TripStopRequest stop = new TripRequest.TripStopRequest(
                2, location, null, null, null, null, null, null, null);
        TripRequest request = new TripRequest(TripType.DELIVERY, location, null, null, List.of(stop));

        assertThatThrownBy(() -> tripService.createTrip(principal(organizationId), request))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("consecutive");
        verify(tripRepository, never()).save(any());
    }

    private AuthenticatedUser principal(UUID organizationId) {
        return new AuthenticatedUser(UUID.randomUUID(), UserRole.MANUFACTURER_ADMIN,
                organizationId, Organizationtype.MANUFACTURER,
                Instant.now().plus(Duration.ofMinutes(5)));
    }
}
