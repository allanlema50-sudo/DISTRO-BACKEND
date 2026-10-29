package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.StockAudit;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.entity.StockMovement;
import com.example.distrobackend.Domain.entity.Warehouse;
import com.example.distrobackend.Domain.enums.StockAuditStatus;
import com.example.distrobackend.Domain.enums.StockMovementType;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.Exception.ResourceNotFoundException;
import com.example.distrobackend.dto.CreateStockAuditRequest;
import com.example.distrobackend.dto.StockAuditResponse;
import com.example.distrobackend.repository.StockAuditRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.StockMovementRepository;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.repository.WarehouseRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockAuditService {

    @PersistenceContext
    private EntityManager entityManager;

    private final StockAuditRepository auditRepository;
    private final OrganizationRepository organizationRepository;
    private final WarehouseRepository warehouseRepository;
    private final StockItemRepository stockItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final UserRepository userRepository;

    @Transactional
    public StockAuditResponse record(AuthenticatedUser actor, CreateStockAuditRequest request) {
        Organization organization = requireOrganizationActor(actor);
        Warehouse warehouse = warehouseRepository.findByIdAndOrganization_IdForUpdate(
                        request.warehouseId(), organization.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND,
                        "Warehouse not found for this organization"));
        StockItem stockItem = stockItemRepository.findByIdAndOrganizationIdForUpdateWithWarehouse(
                        request.stockItemId(), organization.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_ITEM_NOT_FOUND));
        validateWarehouseStock(warehouse, stockItem);
        if (request.countedQuantity() < stockItem.getReservedQuantity()) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "The physical count cannot be lower than the quantity reserved for orders");
        }
        int variance;
        try {
            variance = Math.subtractExact(request.countedQuantity(), stockItem.getQuantityOnHand());
        } catch (ArithmeticException ex) {
            throw new ApiException(ErrorCode.INVALID_STOCK_ADJUSTMENT);
        }
        StockAudit audit = new StockAudit();
        audit.setOrganization(organization);
        audit.setWarehouse(warehouse);
        audit.setStockItem(stockItem);
        audit.setSystemQuantity(stockItem.getQuantityOnHand());
        audit.setCountedQuantity(request.countedQuantity());
        audit.setVariance(variance);
        audit.setStatus(StockAuditStatus.RECORDED);
        audit.setNote(trimToNull(request.note()));
        audit.setRecordedBy(userRepository.getReferenceById(actor.userId()));
        return StockAuditResponse.from(auditRepository.saveAndFlush(audit));
    }

    @Transactional(readOnly = true)
    public Page<StockAuditResponse> list(AuthenticatedUser actor, Pageable pageable) {
        return auditRepository.findByOrganization_IdOrderByCreatedAtDesc(
                        organizationId(actor), pageable)
                .map(StockAuditResponse::from);
    }

    @Transactional(readOnly = true)
    public StockAuditResponse get(AuthenticatedUser actor, UUID auditId) {
        UUID organizationId = organizationId(actor);
        return auditRepository.findByIdWithDetails(auditId, organizationId)
                .map(StockAuditResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Stock audit not found"));
    }

    @Transactional
    public StockAuditResponse apply(AuthenticatedUser actor, UUID auditId) {
        requireOrganizationAdmin(actor);
        UUID organizationId = organizationId(actor);
        StockAudit current = auditRepository.findByIdWithDetails(auditId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Stock audit not found"));
        if (current.getStatus() == StockAuditStatus.APPLIED) {
            return StockAuditResponse.from(current);
        }

        // Keep the same warehouse -> stock lock order used by shipment receipt.
        Warehouse warehouse = warehouseRepository.findByIdAndOrganization_IdForUpdate(
                        current.getWarehouse().getId(), organizationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Warehouse not found"));
        StockItem stockItem = stockItemRepository.findByIdAndOrganizationIdForUpdateWithWarehouse(
                        current.getStockItem().getId(), organizationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.STOCK_ITEM_NOT_FOUND));
        validateWarehouseStock(warehouse, stockItem);

        StockAudit audit = auditRepository.findByIdAndOrganizationIdForUpdate(auditId, organizationId)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND, "Stock audit not found"));
        entityManager.refresh(audit, LockModeType.PESSIMISTIC_WRITE);
        if (audit.getStatus() == StockAuditStatus.APPLIED) {
            return StockAuditResponse.from(audit);
        }
        if (audit.getSystemQuantity() != stockItem.getQuantityOnHand()) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "Stock changed after this audit was recorded; create a new audit before applying it");
        }
        if (audit.getCountedQuantity() < stockItem.getReservedQuantity()) {
            throw new ApiException(ErrorCode.CONFLICT,
                    "The audit would reduce stock below the quantity reserved for orders");
        }

        int delta = audit.getVariance();
        stockItem.setQuantityOnHand(audit.getCountedQuantity());
        if (delta != 0) {
            StockMovement movement = new StockMovement();
            movement.setStockItem(stockItem);
            movement.setMovementType(StockMovementType.ADJUSTMENT);
            movement.setQuantityDelta(delta);
            movement.setReferenceType("STOCK_AUDIT");
            movement.setReferenceId(audit.getId());
            movement.setPerformedBy(userRepository.getReferenceById(actor.userId()));
            movement.setNote(audit.getNote());
            stockMovementRepository.save(movement);
        }
        audit.setStatus(StockAuditStatus.APPLIED);
        audit.setAppliedBy(userRepository.getReferenceById(actor.userId()));
        audit.setAppliedAt(OffsetDateTime.now());
        stockItemRepository.save(stockItem);
        return StockAuditResponse.from(auditRepository.saveAndFlush(audit));
    }

    private Organization requireOrganizationActor(AuthenticatedUser actor) {
        UUID id = organizationId(actor);
        if (actor.role().organizationtype() == null) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        Organization organization = organizationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.NOT_FOUND,
                        "Organization not found"));
        if (organization.getType() != actor.role().organizationtype()) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return organization;
    }

    private void requireOrganizationAdmin(AuthenticatedUser actor) {
        if (actor == null || (actor.role() != UserRole.MANUFACTURER_ADMIN
                && actor.role() != UserRole.DISTRIBUTOR_ADMIN)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }

    private static UUID organizationId(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null
                || actor.role() == null || actor.role().organizationtype() == null) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return actor.organizationId();
    }

    private static void validateWarehouseStock(Warehouse warehouse, StockItem stockItem) {
        if (stockItem.getWarehouse() == null
                || !warehouse.getId().equals(stockItem.getWarehouse().getId())
                || stockItem.getOrganization() == null
                || !warehouse.getOrganization().getId().equals(stockItem.getOrganization().getId())) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "The stock item does not belong to the selected warehouse");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
