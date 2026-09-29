package com.example.distrobackend.service;

import com.example.distrobackend.Domain.enums.OrderStatus;
import com.example.distrobackend.dto.AdminDashboardResponse;
import com.example.distrobackend.repository.OrderRepository;
import com.example.distrobackend.repository.PaymentRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.TripRepository;
import com.example.distrobackend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminDashboardService {

    private final UserRepository userRepository;
    private final StockItemRepository stockItemRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final TripRepository tripRepository;

    public AdminDashboardResponse getDashboard() {

        long totalUsers = userRepository.count();

        long totalProducts = stockItemRepository.count();

        long totalOrders = orderRepository.count();

        long pendingOrders =
                orderRepository.countByStatus(OrderStatus.PENDING);

        long totalPayments = paymentRepository.count();

        long totalTrips = tripRepository.count();

        long lowStockProducts =
                stockItemRepository.countByQuantityOnHandLessThanEqualAndActiveTrue(0);

        return new AdminDashboardResponse(
                totalUsers,
                totalProducts,
                totalOrders,
                pendingOrders,
                totalPayments,
                totalTrips,
                lowStockProducts
        );
    }
}