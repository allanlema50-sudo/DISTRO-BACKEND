package com.example.distrobackend.service;

import com.example.distrobackend.Domain.enums.OrderStatus;
import com.example.distrobackend.Domain.enums.PaymentStatus;
import com.example.distrobackend.Domain.enums.TripStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.OrganizationAnalyticsResponse;
import com.example.distrobackend.dto.ReportSummaryResponse;
import com.example.distrobackend.repository.OrderRepository;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.PaymentRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.TripRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final TripRepository tripRepository;
    private final StockItemRepository stockItemRepository;

    @Transactional(readOnly = true)
    public ReportSummaryResponse overview(AuthenticatedUser actor) {
        requirePlatformAdmin(actor);
        return summary("overview", orderRepository.sumTotalAmount(), Map.of());
    }

    @Transactional(readOnly = true)
    public ReportSummaryResponse revenue(AuthenticatedUser actor) {
        requirePlatformAdmin(actor);
        return summary("revenue", paymentRepository.sumAmountByStatus(PaymentStatus.CONFIRMED),
                paymentStatusCounts());
    }

    @Transactional(readOnly = true)
    public ReportSummaryResponse orders(AuthenticatedUser actor) {
        requirePlatformAdmin(actor);
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (OrderStatus status : OrderStatus.values()) counts.put(status.name(), orderRepository.countByStatus(status));
        return summary("orders", orderRepository.sumTotalAmount(), counts);
    }

    @Transactional(readOnly = true)
    public ReportSummaryResponse payments(AuthenticatedUser actor) {
        requirePlatformAdmin(actor);
        return summary("payments", paymentRepository.sumAmountByStatus(PaymentStatus.CONFIRMED),
                paymentStatusCounts());
    }

    @Transactional(readOnly = true)
    public ReportSummaryResponse deliveries(AuthenticatedUser actor) {
        requirePlatformAdmin(actor);
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (TripStatus status : TripStatus.values()) counts.put(status.name(), tripRepository.countByStatus(status));
        return summary("deliveries", BigDecimal.ZERO, counts);
    }

    @Transactional(readOnly = true)
    public ReportSummaryResponse inventory(AuthenticatedUser actor) {
        requirePlatformAdmin(actor);
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        counts.put("ACTIVE", stockItemRepository.countByActiveTrue());
        counts.put("INACTIVE", stockItemRepository.countByActiveFalse());
        counts.put("LOW_STOCK", stockItemRepository.countLowStock());
        return summary("inventory", BigDecimal.ZERO, counts);
    }

    @Transactional(readOnly = true)
    public Page<OrganizationAnalyticsResponse> organizations(AuthenticatedUser actor, Pageable pageable) {
        requirePlatformAdmin(actor);
        return organizationRepository.findAll(pageable).map(organization ->
                new OrganizationAnalyticsResponse(organization.getId(), organization.getName(), organization.getType(),
                        userRepository.countByOrganization_Id(organization.getId()),
                        orderRepository.countByOrganization_Id(organization.getId()),
                        paymentRepository.countByOrder_Organization_Id(organization.getId()),
                        stockItemRepository.countByOrganization_Id(organization.getId()),
                        tripRepository.countByOrganization_Id(organization.getId())));
    }

    private ReportSummaryResponse summary(String report, BigDecimal amount, Map<String, Long> byStatus) {
        return new ReportSummaryResponse(report, organizationRepository.count(), userRepository.count(),
                orderRepository.count(), paymentRepository.count(), tripRepository.count(),
                stockItemRepository.count(), stockItemRepository.countLowStock(),
                amount == null ? BigDecimal.ZERO : amount, byStatus);
    }

    private Map<String, Long> paymentStatusCounts() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (PaymentStatus status : PaymentStatus.values()) counts.put(status.name(), paymentRepository.countByStatus(status));
        return counts;
    }

    private static void requirePlatformAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }
}
