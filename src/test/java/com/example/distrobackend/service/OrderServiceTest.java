package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.Order;
import com.example.distrobackend.Domain.entity.OrderItem;
import com.example.distrobackend.Domain.entity.StockMovement;
import com.example.distrobackend.Domain.entity.Payment;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.entity.User;
import com.example.distrobackend.Domain.entity.Warehouse;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Domain.enums.UserStatus;
import com.example.distrobackend.Exception.InsufficientStockException;
import com.example.distrobackend.dto.OrderItemRequest;
import com.example.distrobackend.dto.OrderRequest;
import com.example.distrobackend.repository.OrderRepository;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.PaymentRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.StockMovementRepository;
import com.example.distrobackend.repository.StockLocationRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock private OrderRepository orderRepository;
    @Mock private StockItemRepository stockItemRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private StockLocationRepository stockLocationRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private OrganizationNotificationService notificationService;
    @Mock private PlatformSettingsService platformSettingsService;

    @InjectMocks private OrderService orderService;

    private UUID customerId;
    private UUID distributorId;
    private UUID stockItemId;
    private Organization distributor;
    private StockItem offer;
    private User customer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "reservationTtl", Duration.ofMinutes(15));
        ReflectionTestUtils.setField(orderService, "maxOpenPendingOrdersPerCustomer", 5);
        customerId = UUID.randomUUID();
        distributorId = UUID.randomUUID();
        stockItemId = UUID.randomUUID();
        distributor = organization(distributorId, Organizationtype.DISTRIBUTOR);
        Organization manufacturer = organization(UUID.randomUUID(), Organizationtype.MANUFACTURER);
        Warehouse warehouse = new Warehouse();
        warehouse.setId(UUID.randomUUID());
        warehouse.setOrganization(distributor);
        warehouse.setActive(true);

        StockItem source = new StockItem();
        source.setId(UUID.randomUUID());
        source.setOrganization(manufacturer);

        offer = new StockItem();
        offer.setId(stockItemId);
        offer.setOrganization(distributor);
        offer.setSourceStockItem(source);
        offer.setWarehouse(warehouse);
        offer.setSku("DIST-CEMENT");
        offer.setName("Cement");
        offer.setUnitPrice(new BigDecimal("1200.00"));
        offer.setQuantityOnHand(10);
        offer.setReservedQuantity(0);
        offer.setActive(true);

        customer = new User();
        customer.setId(customerId);
    }

    @Test
    void orderCreationAtomicallyReservesAvailableStock() {
        when(platformSettingsService.resolveDeliveryFee()).thenReturn(BigDecimal.ZERO);
        when(userRepository.findByIdForUpdate(customerId)).thenReturn(Optional.of(customer));
        when(organizationRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(stockItemRepository.findByIdForUpdate(stockItemId)).thenReturn(Optional.of(offer));
        when(orderRepository.getNextOrderSequence()).thenReturn(42L);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderRequest request = request(3);
        var response = orderService.createOrder(customerId, request);

        assertThat(response.status()).isEqualTo(com.example.distrobackend.Domain.enums.OrderStatus.PENDING);
        assertThat(offer.getReservedQuantity()).isEqualTo(3);
        assertThat(offer.getAvailableQuantity()).isEqualTo(7);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void distributorCannotCreateAnOrderForAnotherOrganization() {
        AuthenticatedUser actor = new AuthenticatedUser(
                UUID.randomUUID(), UserRole.DISTRIBUTOR_ADMIN, distributorId,
                Organizationtype.DISTRIBUTOR, Instant.now().plusSeconds(300));
        OrderRequest request = new OrderRequest(
                UUID.randomUUID(), "1 Main Street", null, null,
                List.of(new OrderItemRequest(stockItemId, 1)), customerId);

        assertThatThrownBy(() -> orderService.createOrderForDistributor(actor, request))
                .isInstanceOf(com.example.distrobackend.Exception.ApiException.class)
                .hasMessageContaining("distributor's organization");

        verify(userRepository, never()).findById(any());
    }

    @Test
    void distributorCanCreateAnOrderForAnExistingLinkedCustomer() {
        AuthenticatedUser actor = new AuthenticatedUser(
                UUID.randomUUID(), UserRole.DISTRIBUTOR_STAFF, distributorId,
                Organizationtype.DISTRIBUTOR, Instant.now().plusSeconds(300));
        customer.setRole(UserRole.CUSTOMER);
        customer.setStatus(UserStatus.ACTIVE);

        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(orderRepository.existsByOrganization_IdAndCustomer_Id(distributorId, customerId))
                .thenReturn(true);
        when(platformSettingsService.resolveDeliveryFee()).thenReturn(BigDecimal.ZERO);
        when(userRepository.findByIdForUpdate(customerId)).thenReturn(Optional.of(customer));
        when(organizationRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(stockItemRepository.findByIdForUpdate(stockItemId)).thenReturn(Optional.of(offer));
        when(orderRepository.getNextOrderSequence()).thenReturn(43L);
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderRequest request = new OrderRequest(
                null, "1 Main Street", null, null,
                List.of(new OrderItemRequest(stockItemId, 2)), customerId);

        var response = orderService.createOrderForDistributor(actor, request);

        assertThat(response.customerId()).isEqualTo(customerId);
        assertThat(response.organizationId()).isEqualTo(distributorId);
        assertThat(offer.getReservedQuantity()).isEqualTo(2);
    }

    @Test
    void insufficientStockRejectsOrderAndNotifiesDistributor() {
        when(userRepository.findByIdForUpdate(customerId)).thenReturn(Optional.of(customer));
        when(organizationRepository.findById(distributorId)).thenReturn(Optional.of(distributor));
        when(stockItemRepository.findByIdForUpdate(stockItemId)).thenReturn(Optional.of(offer));

        assertThatThrownBy(() -> orderService.createOrder(customerId, request(11)))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("requested 11");

        assertThat(offer.getReservedQuantity()).isZero();
        verify(notificationService).notify(
                eq(distributor),
                eq("ORDER_STOCK_REJECTED"),
                eq("Order rejected: insufficient stock"),
                contains("only 10 units were available"),
                eq("CUSTOMER_ORDER_ATTEMPT"),
                isNull());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void paymentConfirmationKeepsStockReservedUntilTripDispatch() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus(com.example.distrobackend.Domain.enums.OrderStatus.PENDING);
        OrderItem line = new OrderItem();
        line.setStockItem(offer);
        line.setQuantity(3);
        line.setStockCheckStatus("RESERVED");
        order.addOrderItem(line);
        offer.setReservedQuantity(3);
        when(orderRepository.findByIdWithItemsForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.confirmPaidOrder(order.getId());

        assertThat(order.getStatus()).isEqualTo(com.example.distrobackend.Domain.enums.OrderStatus.CONFIRMED);
        assertThat(offer.getQuantityOnHand()).isEqualTo(10);
        assertThat(offer.getReservedQuantity()).isEqualTo(3);
        assertThat(line.getStockCheckStatus()).isEqualTo("RESERVED");
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    void tripDispatchDeductsReservedStockAndRecordsOneSaleMovement() {
        UUID orderId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(com.example.distrobackend.Domain.enums.OrderStatus.AWAITING_DISPATCH);
        order.setOrganization(distributor);
        order.setCustomer(customer);
        order.setOrderNumber("ORD-TEST");
        order.setSubtotalAmount(BigDecimal.ZERO);
        order.setDeliveryFee(BigDecimal.ZERO);
        order.setTotalAmount(BigDecimal.ZERO);
        order.setPlacedAt(OffsetDateTime.now());
        OrderItem line = new OrderItem();
        line.setStockItem(offer);
        line.setQuantity(3);
        line.setUnitPrice(BigDecimal.ONE);
        line.setStockCheckStatus("RESERVED");
        order.addOrderItem(line);
        offer.setReservedQuantity(3);
        User staff = new User();
        staff.setId(UUID.randomUUID());
        AuthenticatedUser actor = new AuthenticatedUser(staff.getId(), UserRole.DISTRIBUTOR_ADMIN,
                distributorId, Organizationtype.DISTRIBUTOR, Instant.now().plusSeconds(300));
        when(orderRepository.findByIdWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findById(staff.getId())).thenReturn(Optional.of(staff));
        when(stockItemRepository.findByIdForUpdate(stockItemId)).thenReturn(Optional.of(offer));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        orderService.dispatchFromTrip(actor, orderId);

        assertThat(order.getStatus()).isEqualTo(com.example.distrobackend.Domain.enums.OrderStatus.IN_TRANSIT);
        assertThat(offer.getQuantityOnHand()).isEqualTo(7);
        assertThat(offer.getReservedQuantity()).isZero();
        assertThat(line.getStockCheckStatus()).isEqualTo("COMMITTED");
        org.mockito.ArgumentCaptor<StockMovement> movement =
                org.mockito.ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(movement.capture());
        assertThat(movement.getValue().getQuantityDelta()).isEqualTo(-3);
        assertThat(movement.getValue().getReferenceId()).isEqualTo(orderId);
        assertThat(movement.getValue().getMovementType()).isEqualTo(
                com.example.distrobackend.Domain.enums.StockMovementType.SALE_OUT);
    }

    @Test
    void genericStatusUpdateCannotBypassTripDispatch() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus(com.example.distrobackend.Domain.enums.OrderStatus.AWAITING_DISPATCH);
        when(orderRepository.findByIdWithItemsForUpdate(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(order.getId(), customerId,
                new com.example.distrobackend.dto.OrderStatusUpdate(
                        com.example.distrobackend.Domain.enums.OrderStatus.IN_TRANSIT, "skip dispatch")))
                .isInstanceOf(com.example.distrobackend.Exception.ApiException.class)
                .hasMessageContaining("assigned delivery trip");
        verify(stockItemRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void pickingAndPackingPersistProgressBeforeDispatch() {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(com.example.distrobackend.Domain.enums.OrderStatus.CONFIRMED);
        order.setOrganization(distributor);
        order.setCustomer(customer);
        order.setOrderNumber("ORD-WH-1");
        order.setSubtotalAmount(BigDecimal.ZERO);
        order.setDeliveryFee(BigDecimal.ZERO);
        order.setTotalAmount(BigDecimal.ZERO);
        order.setPlacedAt(OffsetDateTime.now());
        OrderItem line = new OrderItem();
        line.setId(itemId);
        line.setStockItem(offer);
        line.setQuantity(2);
        line.setUnitPrice(BigDecimal.ONE);
        line.setStockCheckStatus("RESERVED");
        order.addOrderItem(line);
        User staff = new User();
        staff.setId(UUID.randomUUID());
        AuthenticatedUser actor = new AuthenticatedUser(staff.getId(), UserRole.DISTRIBUTOR_ADMIN,
                distributorId, Organizationtype.DISTRIBUTOR, Instant.now().plusSeconds(300));
        when(orderRepository.findByIdWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));
        when(userRepository.findById(staff.getId())).thenReturn(Optional.of(staff));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var picked = orderService.pickOrderItem(actor, orderId,
                new com.example.distrobackend.dto.PickOrderItemRequest(itemId, 2));
        assertThat(picked.status()).isEqualTo(com.example.distrobackend.Domain.enums.OrderStatus.READY_TO_PACK);
        assertThat(line.getPickedQuantity()).isEqualTo(2);
        assertThat(offer.getQuantityOnHand()).isEqualTo(10);

        var packed = orderService.packOrder(actor, orderId,
                new com.example.distrobackend.dto.PackOrderRequest(1));
        assertThat(packed.status()).isEqualTo(com.example.distrobackend.Domain.enums.OrderStatus.AWAITING_DISPATCH);
        assertThat(packed.packageCount()).isEqualTo(1);
        assertThat(line.getPackedQuantity()).isEqualTo(2);
        assertThat(offer.getReservedQuantity()).isZero();
    }

    @Test
    void pickingCannotExceedRequestedQuantity() {
        UUID orderId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Order order = new Order();
        order.setId(orderId);
        order.setStatus(com.example.distrobackend.Domain.enums.OrderStatus.PROCESSING);
        order.setOrganization(distributor);
        OrderItem line = new OrderItem();
        line.setId(itemId);
        line.setStockItem(offer);
        line.setQuantity(2);
        order.addOrderItem(line);
        AuthenticatedUser actor = new AuthenticatedUser(UUID.randomUUID(), UserRole.DISTRIBUTOR_STAFF,
                distributorId, Organizationtype.DISTRIBUTOR, Instant.now().plusSeconds(300));
        when(orderRepository.findByIdWithItemsForUpdate(orderId)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.pickOrderItem(actor, orderId,
                new com.example.distrobackend.dto.PickOrderItemRequest(itemId, 3)))
                .isInstanceOf(com.example.distrobackend.Exception.ApiException.class)
                .hasMessageContaining("already picked and requested");
        assertThat(line.getPickedQuantity()).isZero();
    }

    @Test
    void expiredPendingOrderReleasesReservationAndFailsOrder() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus(com.example.distrobackend.Domain.enums.OrderStatus.PENDING);
        order.setReservationExpiresAt(OffsetDateTime.now().minusMinutes(1));
        OrderItem orderItem = new OrderItem();
        orderItem.setStockItem(offer);
        orderItem.setQuantity(4);
        orderItem.setStockCheckStatus("RESERVED");
        order.addOrderItem(orderItem);
        offer.setReservedQuantity(4);

        when(orderRepository.findByIdWithItemsForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(order.getId())).thenReturn(Optional.empty());
        when(stockItemRepository.findByIdForUpdate(stockItemId)).thenReturn(Optional.of(offer));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        assertThat(orderService.expireReservationAndFailOrder(order.getId(), OffsetDateTime.now())).isTrue();

        assertThat(order.getStatus()).isEqualTo(com.example.distrobackend.Domain.enums.OrderStatus.FAILED);
        assertThat(order.getReservationExpiresAt()).isNull();
        assertThat(offer.getReservedQuantity()).isZero();
        assertThat(orderItem.getStockCheckStatus()).isEqualTo("RELEASED");
    }

    @Test
    void expiryKeepsOrderPendingWhenProviderAcceptedStkRequestIsInFlight() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus(com.example.distrobackend.Domain.enums.OrderStatus.PENDING);
        order.setReservationExpiresAt(OffsetDateTime.now().minusMinutes(1));
        Payment payment = new Payment();
        payment.setOrder(order);
        payment.setStatus(com.example.distrobackend.Domain.enums.PaymentStatus.PENDING);
        payment.setMpesaCheckoutRequestId("ws_CO_in_flight");

        when(orderRepository.findByIdWithItemsForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(order.getId())).thenReturn(Optional.of(payment));

        assertThat(orderService.expireReservationAndFailOrder(order.getId(), OffsetDateTime.now())).isFalse();

        assertThat(order.getStatus()).isEqualTo(com.example.distrobackend.Domain.enums.OrderStatus.PENDING);
        assertThat(order.getReservationExpiresAt()).isNotNull();
        assertThat(order.getReservationExpiresAt()).isBefore(OffsetDateTime.now());
        verify(stockItemRepository, never()).findByIdForUpdate(any());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void dispatchedOrderCannotBeCancelledThroughStatusEndpoint() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus(com.example.distrobackend.Domain.enums.OrderStatus.IN_TRANSIT);

        when(orderRepository.findByIdWithItemsForUpdate(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.updateOrderStatus(
                order.getId(), customerId,
                new com.example.distrobackend.dto.OrderStatusUpdate(
                        com.example.distrobackend.Domain.enums.OrderStatus.CANCELLED,
                        "Customer changed mind")))
                .isInstanceOf(com.example.distrobackend.Exception.ApiException.class)
                .hasMessageContaining("Cannot transition from IN_TRANSIT to CANCELLED");

        verify(paymentRepository, never()).findByOrderIdForUpdate(any());
        verify(userRepository, never()).findById(any());
        verify(stockItemRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void customerCannotCancelOrderAfterItLeavesPendingState() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus(com.example.distrobackend.Domain.enums.OrderStatus.CONFIRMED);

        when(orderRepository.findByIdWithItemsForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(userRepository.findById(customerId)).thenReturn(Optional.of(customer));

        assertThatThrownBy(() -> orderService.updateOrderStatus(
                order.getId(), customerId,
                new com.example.distrobackend.dto.OrderStatusUpdate(
                        com.example.distrobackend.Domain.enums.OrderStatus.CANCELLED,
                        "Customer changed mind")))
                .isInstanceOf(com.example.distrobackend.Exception.ApiException.class)
                .hasMessageContaining("Customers may only cancel unpaid pending orders");

        verify(stockItemRepository, never()).findByIdForUpdate(any());
        verify(orderRepository, never()).save(any(Order.class));
    }

    @Test
    void orderWithAcceptedStkRequestCannotBeCancelledBeforeCallback() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus(com.example.distrobackend.Domain.enums.OrderStatus.PENDING);
        Payment payment = new Payment();
        payment.setStatus(com.example.distrobackend.Domain.enums.PaymentStatus.PENDING);
        payment.setMpesaCheckoutRequestId("ws_CO_in_flight");

        when(orderRepository.findByIdWithItemsForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(order.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> orderService.updateOrderStatus(
                order.getId(), customerId,
                new com.example.distrobackend.dto.OrderStatusUpdate(
                        com.example.distrobackend.Domain.enums.OrderStatus.CANCELLED,
                        "Cancel")))
                .isInstanceOf(com.example.distrobackend.Exception.ApiException.class)
                .hasMessageContaining("in-flight M-Pesa payment");

        verify(userRepository, never()).findById(any());
        verify(stockItemRepository, never()).findByIdForUpdate(any());
    }

    @Test
    void orderWithAcceptedStkRequestCannotBeManuallyFailedBeforeCallback() {
        Order order = new Order();
        order.setId(UUID.randomUUID());
        order.setStatus(com.example.distrobackend.Domain.enums.OrderStatus.PENDING);
        Payment payment = new Payment();
        payment.setStatus(com.example.distrobackend.Domain.enums.PaymentStatus.PENDING);
        payment.setMpesaCheckoutRequestId("ws_CO_in_flight");

        when(orderRepository.findByIdWithItemsForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdForUpdate(order.getId())).thenReturn(Optional.of(payment));

        assertThatThrownBy(() -> orderService.updateOrderStatus(
                order.getId(), distributorId,
                new com.example.distrobackend.dto.OrderStatusUpdate(
                        com.example.distrobackend.Domain.enums.OrderStatus.FAILED,
                        "Warehouse rejected order")))
                .isInstanceOf(com.example.distrobackend.Exception.ApiException.class)
                .hasMessageContaining("cannot be cancelled or failed until it is reconciled");

        verify(userRepository, never()).findById(any());
        verify(stockItemRepository, never()).findByIdForUpdate(any());
    }

    private OrderRequest request(int quantity) {
        return new OrderRequest(
                distributorId,
                "1 Main Street",
                -1.2864,
                36.8172,
                List.of(new OrderItemRequest(stockItemId, quantity)));
    }

    private static Organization organization(UUID id, Organizationtype type) {
        Organization organization = new Organization();
        organization.setId(id);
        organization.setType(type);
        return organization;
    }
}
