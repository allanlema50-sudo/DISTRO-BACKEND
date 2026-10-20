package com.example.distrobackend.service;

import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.OrganizationDashboardResponse;
import com.example.distrobackend.repository.OrderRepository;
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
public class OrganizationDashboardService {

    private final UserRepository userRepository;
    private final StockItemRepository stockItemRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final TripRepository tripRepository;

    @Transactional(readOnly = true)
    public OrganizationDashboardResponse get(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null
                || (actor.role() != UserRole.MANUFACTURER_ADMIN
                && actor.role() != UserRole.MANUFACTURER_STAFF
                && actor.role() != UserRole.DISTRIBUTOR_ADMIN
                && actor.role() != UserRole.DISTRIBUTOR_STAFF)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return new OrganizationDashboardResponse(actor.organizationId(),
                userRepository.countByOrganization_Id(actor.organizationId()),
                stockItemRepository.countByOrganization_Id(actor.organizationId()),
                orderRepository.countByOrganization_Id(actor.organizationId()),
                paymentRepository.countByOrder_Organization_Id(actor.organizationId()),
                tripRepository.countByOrganization_Id(actor.organizationId()));
    }
}
