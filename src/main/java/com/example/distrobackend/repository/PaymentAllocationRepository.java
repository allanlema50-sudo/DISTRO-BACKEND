package com.example.distrobackend.repository;
import com.example.distrobackend.Domain.entity.PaymentAllocation;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, UUID> {
 List<PaymentAllocation> findByPayment_IdOrderByCreatedAtAsc(UUID paymentId);
 List<PaymentAllocation> findByPayment_IdInOrderByCreatedAtAsc(java.util.Collection<UUID> paymentIds);
 List<PaymentAllocation> findByPurchaseOrder_SupplierOrganization_IdAndPayment_Id(UUID supplierId, UUID paymentId);
 List<PaymentAllocation> findByPurchaseOrder_Id(UUID purchaseOrderId);
}
