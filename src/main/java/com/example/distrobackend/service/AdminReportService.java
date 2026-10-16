
package com.example.distrobackend.service;

import com.example.distrobackend.Domain.enums.OrderStatus;
import com.example.distrobackend.Domain.enums.PaymentStatus;
import com.example.distrobackend.Domain.enums.TripStatus;
import com.example.distrobackend.Domain.enums.TripType;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.dto.AdminReportOverviewResponse;
import com.example.distrobackend.repository.OrderRepository;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.PaymentRepository;
import com.example.distrobackend.repository.ProductRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.TripRepository;
import com.example.distrobackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.distrobackend.Domain.entity.Order;
import com.example.distrobackend.Domain.entity.Payment;
import com.example.distrobackend.dto.MonthlyOrderPoint;
import com.example.distrobackend.dto.MonthlyRevenuePoint;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class AdminReportService {

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final TripRepository tripRepository;
    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final StockItemRepository stockItemRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public AdminReportOverviewResponse getOverview() {

        long totalOrders = orderRepository.count();
        long pendingOrders = orderRepository.countByStatus(OrderStatus.PENDING);

        long totalPayments = paymentRepository.count();
        long confirmedPayments =
                paymentRepository.countByStatus(PaymentStatus.CONFIRMED);

        BigDecimal totalRevenue = paymentRepository.sumAmountByStatus(
                PaymentStatus.CONFIRMED
        );

        long totalDeliveries =
                tripRepository.countByTripType(TripType.DELIVERY);

        long completedDeliveries =
                tripRepository.countByTripTypeAndStatus(
                        TripType.DELIVERY,
                        TripStatus.COMPLETED
                );

        long totalCustomers = userRepository.countByRole(UserRole.CUSTOMER);
        long totalOrganizations = organizationRepository.count();

        long totalStockItems = stockItemRepository.countByActiveTrue();

        long lowStockItems = stockItemRepository.countLowStockItems();
        long outOfStockItems = stockItemRepository.countOutOfStockItems();

        BigDecimal inventoryValue = stockItemRepository.calculateInventoryValue();

        return new AdminReportOverviewResponse(
                totalOrders,
                pendingOrders,
                totalRevenue == null ? BigDecimal.ZERO : totalRevenue,
                totalPayments,
                confirmedPayments,
                totalDeliveries,
                completedDeliveries,
                totalCustomers,
                totalOrganizations,
                totalStockItems,
                lowStockItems,
                outOfStockItems,
                inventoryValue == null ? BigDecimal.ZERO : inventoryValue
        );
    }
    @Transactional(readOnly = true)
public List<MonthlyOrderPoint> getOrderReport(String period) {
    OffsetDateTime start = getStartDate(period);
    List<Order> orders =
            orderRepository.findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(start);

    Map<YearMonth, Long> monthlyCounts = new TreeMap<>();

    YearMonth firstMonth = YearMonth.from(start);
    YearMonth currentMonth = YearMonth.now();

    for (YearMonth month = firstMonth;
         !month.isAfter(currentMonth);
         month = month.plusMonths(1)) {
        monthlyCounts.put(month, 0L);
    }

    for (Order order : orders) {
        if (order.getCreatedAt() != null) {
            YearMonth month = YearMonth.from(order.getCreatedAt());
            monthlyCounts.merge(month, 1L, Long::sum);
        }
    }

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy");
    List<MonthlyOrderPoint> result = new ArrayList<>();

    monthlyCounts.forEach((month, count) ->
            result.add(new MonthlyOrderPoint(month.format(formatter), count)));

    return result;
}

@Transactional(readOnly = true)
public List<MonthlyRevenuePoint> getRevenueReport(String period) {
    OffsetDateTime start = getStartDate(period);

    List<Payment> payments =
            paymentRepository
                    .findByStatusAndCreatedAtGreaterThanEqualOrderByCreatedAtAsc(
                            PaymentStatus.CONFIRMED,
                            start
                    );

    Map<YearMonth, BigDecimal> monthlyRevenue = new TreeMap<>();

    YearMonth firstMonth = YearMonth.from(start);
    YearMonth currentMonth = YearMonth.now();

    for (YearMonth month = firstMonth;
         !month.isAfter(currentMonth);
         month = month.plusMonths(1)) {
        monthlyRevenue.put(month, BigDecimal.ZERO);
    }

    for (Payment payment : payments) {
        if (payment.getCreatedAt() != null && payment.getAmount() != null) {
            YearMonth month = YearMonth.from(payment.getCreatedAt());
            monthlyRevenue.merge(month, payment.getAmount(), BigDecimal::add);
        }
    }

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM yyyy");
    List<MonthlyRevenuePoint> result = new ArrayList<>();

    monthlyRevenue.forEach((month, amount) ->
            result.add(new MonthlyRevenuePoint(month.format(formatter), amount)));

    return result;
}

private OffsetDateTime getStartDate(String period) {
    LocalDate today = LocalDate.now();

    LocalDate startDate = switch (period == null ? "12_MONTHS" : period) {
        case "7_DAYS" -> today.minusDays(6);
        case "30_DAYS" -> today.minusDays(29);
        case "3_MONTHS" -> today.minusMonths(2).withDayOfMonth(1);
        case "6_MONTHS" -> today.minusMonths(5).withDayOfMonth(1);
        case "12_MONTHS" -> today.minusMonths(11).withDayOfMonth(1);
        default -> today.minusMonths(11).withDayOfMonth(1);
    };

    return startDate.atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
}
}

