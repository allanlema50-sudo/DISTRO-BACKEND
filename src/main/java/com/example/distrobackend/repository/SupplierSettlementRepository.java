package com.example.distrobackend.repository;
import com.example.distrobackend.Domain.entity.SupplierSettlement;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
public interface SupplierSettlementRepository extends JpaRepository<SupplierSettlement, UUID> { List<SupplierSettlement> findBySupplierOrganization_IdOrderByCreatedAtDesc(UUID supplierId); java.util.Optional<SupplierSettlement> findByAllocation_Id(UUID allocationId); }
