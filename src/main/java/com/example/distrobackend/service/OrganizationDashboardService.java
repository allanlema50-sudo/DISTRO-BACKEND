package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.enums.OrderStatus;
import com.example.distrobackend.Domain.enums.PaymentStatus;
import com.example.distrobackend.Domain.enums.ProductApprovalStatus;
import com.example.distrobackend.Domain.enums.ProductStatus;
import com.example.distrobackend.Domain.enums.TripStatus;
import com.example.distrobackend.dto.OrganizationDashboardResponse;
import com.example.distrobackend.repository.OrderRepository;
import com.example.distrobackend.repository.PaymentRepository;
import com.example.distrobackend.repository.ProductRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.TripRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrganizationDashboardService {

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final StockItemRepository stockItemRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final TripRepository tripRepository;

    @Transactional(readOnly = true)
    public OrganizationDashboardResponse getDashboard(
            AuthenticatedUser me
    ) {

        User user = userRepository.findById(me.userId())
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Authenticated user not found"
                        )
                );

        if (user.getOrganization() == null) {
            throw new IllegalStateException(
                    "This user is not assigned to an organization"
            );
        }

        UUID organizationId =
                user.getOrganization().getId();

        String organizationName =
                user.getOrganization().getName();

        String organizationType =
                user.getOrganization()
                        .getType()
                        .name();

        long totalProducts =
                productRepository.countByOrganizationId(
                        organizationId
                );

        long activeProducts =
                productRepository
                        .countByOrganizationIdAndStatus(
                                organizationId,
                                ProductStatus.ACTIVE
                        );

        long totalStockUnits =
                stockItemRepository
                        .sumQuantityOnHandByOrganizationId(
                                organizationId
                        );

        long lowStockProducts =
                stockItemRepository
                        .countByOrganizationIdAndQuantityOnHandLessThanEqualAndActiveTrue(
                                organizationId,
                                10
                        );

        long pendingStockApprovals =
                stockItemRepository
                        .countByOrganizationIdAndApprovalStatus(
                                organizationId,
                                ProductApprovalStatus.PENDING
                        );

        long totalOrders =
                orderRepository.countByOrganizationId(
                        organizationId
                );

        long pendingOrders =
                orderRepository
                        .countByOrganizationIdAndStatus(
                                organizationId,
                                OrderStatus.PENDING
                        );

        long totalPayments =
                paymentRepository.countByOrganizationId(
                        organizationId
                );

        long pendingPayments =
                paymentRepository
                        .countByOrganizationIdAndStatus(
                                organizationId,
                                PaymentStatus.PENDING
                        );

        long totalTrips =
                tripRepository.countByOrganizationId(
                        organizationId
                );

        long activeTrips =
                tripRepository.countActiveByOrganizationId(
                        organizationId,
                        EnumSet.of(
                                TripStatus.ASSIGNED,
                                TripStatus.IN_PROGRESS
                        )
                );

        long totalUsers =
                userRepository.countByOrganizationId(
                        organizationId
                );

        return new OrganizationDashboardResponse(
                organizationId,
                organizationName,
                organizationType,
                totalUsers,
                totalProducts,
                activeProducts,
                totalStockUnits,
                lowStockProducts,
                pendingStockApprovals,
                totalOrders,
                pendingOrders,
                totalPayments,
                pendingPayments,
                totalTrips,
                activeTrips
        );
    }
}
