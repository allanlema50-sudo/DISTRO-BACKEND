package com.example.distrobackend.service;

import com.example.distrobackend.Domain.enums.AccessRequestStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.AdminDashboardResponse;
import com.example.distrobackend.repository.AccessRequestRepository;
import com.example.distrobackend.repository.OrderRepository;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.PaymentRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.TripRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final AccessRequestRepository accessRequestRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final TripRepository tripRepository;
    private final StockItemRepository stockItemRepository;

    @Transactional(readOnly = true)
    public AdminDashboardResponse get(AuthenticatedUser actor) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return new AdminDashboardResponse(organizationRepository.count(), userRepository.count(),
                userRepository.countByStatus(UserStatus.ACTIVE),
                accessRequestRepository.countByStatus(AccessRequestStatus.PENDING),
                orderRepository.count(), paymentRepository.count(), tripRepository.count(),
                stockItemRepository.count());
    }
}
