package com.example.distrobackend.mapper;

import com.example.distrobackend.Domain.entity.Order;
import com.example.distrobackend.Domain.entity.OrderItem;
import com.example.distrobackend.dto.OrderItemResponse;
import com.example.distrobackend.dto.OrderResponse;

import java.math.BigDecimal;

public class OrderMapper {

    private OrderMapper() { }

    public static OrderResponse toResponse(Order order) {
        if (order == null) return null;
        return new OrderResponse(order.getId(), order.getOrderNumber(),
                order.getCustomer() == null ? null : order.getCustomer().getId(),
                order.getOrganization() == null ? null : order.getOrganization().getId(), order.getStatus(),
                order.getDeliveryAddress(), order.getDeliveryLat(), order.getDeliveryLng(),
                order.getSubtotalAmount(), order.getDeliveryFee(), order.getTotalAmount(),
                order.getPackageCount(), order.getCancellationReason(), order.getPlacedAt(), order.getReservationExpiresAt(),
                order.getConfirmedAt(), order.getDeliveredAt(), order.getDeliveryEmailStatus(),
                order.getDeliveryEmailSentAt(), order.getCancelledAt(),
                order.getOrderItems().stream().map(OrderMapper::toItemResponse).toList());
    }

    public static OrderItemResponse toItemResponse(OrderItem item) {
        if (item == null) return null;
        BigDecimal lineTotal = item.getLineTotal() != null ? item.getLineTotal()
                : item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()));
        return new OrderItemResponse(item.getId(), item.getStockItem() == null ? null : item.getStockItem().getId(),
                item.getQuantity(), item.getPickedQuantity(), item.getPackedQuantity(), item.getUnitPrice(), lineTotal);
    }
}
