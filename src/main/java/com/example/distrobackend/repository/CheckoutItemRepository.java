package com.example.distrobackend.repository;
import com.example.distrobackend.Domain.entity.CheckoutItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface CheckoutItemRepository extends JpaRepository<CheckoutItem, UUID> { List<CheckoutItem> findByCheckout_Id(UUID checkoutId); List<CheckoutItem> findByPurchaseOrder_Id(UUID purchaseOrderId); }
