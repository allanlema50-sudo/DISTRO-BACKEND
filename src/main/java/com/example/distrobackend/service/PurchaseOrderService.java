package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.PurchaseOrder;
import com.example.distrobackend.Domain.entity.PurchaseOrderItem;
import com.example.distrobackend.Domain.entity.PurchaseOrderSettlement;
import com.example.distrobackend.Domain.entity.StockItem;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.PurchaseOrderStatus;
import com.example.distrobackend.Domain.enums.SettlementStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.CreatePurchaseOrderRequest;
import com.example.distrobackend.dto.PurchaseOrderDecisionRequest;
import com.example.distrobackend.dto.PurchaseOrderItemResponse;
import com.example.distrobackend.dto.PurchaseOrderResponse;
import com.example.distrobackend.dto.PurchaseOrderSettlementResponse;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.PurchaseOrderRepository;
import com.example.distrobackend.repository.PurchaseOrderSettlementRepository;
import com.example.distrobackend.repository.StockItemRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PurchaseOrderService {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderSettlementRepository settlementRepository;
    private final OrganizationRepository organizationRepository;
    private final StockItemRepository stockItemRepository;
    private final UserRepository userRepository;

    @Transactional
    public PurchaseOrderResponse create(AuthenticatedUser actor, CreatePurchaseOrderRequest request) {
        requireDistributor(actor);
        Organization buyer = organizationRepository.findById(actor.organizationId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Buyer organization not found"));
        Organization supplier = organizationRepository.findById(request.supplierOrganizationId())
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Supplier organization not found"));
        if (supplier.getType() != Organizationtype.MANUFACTURER) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Purchase orders must target a manufacturer");
        }
        if (buyer.getId().equals(supplier.getId())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Buyer and supplier organizations must differ");
        }

        Set<UUID> seenItems = new HashSet<>();
        PurchaseOrder purchaseOrder = new PurchaseOrder();
        purchaseOrder.setPurchaseOrderNumber(String.format("PO-%d", purchaseOrderRepository.getNextPurchaseOrderSequence()));
        purchaseOrder.setBuyerOrganization(buyer);
        purchaseOrder.setSupplierOrganization(supplier);
        purchaseOrder.setCreatedBy(userRepository.getReferenceById(actor.userId()));
        purchaseOrder.setStatus(PurchaseOrderStatus.SUBMITTED);
        purchaseOrder.setSubmittedAt(OffsetDateTime.now());

        BigDecimal total = BigDecimal.ZERO;
        for (var requestItem : request.items()) {
            if (!seenItems.add(requestItem.stockItemId())) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Each stock item may appear only once");
            }
            StockItem stockItem = stockItemRepository
                    .findByIdAndOrganizationIdForUpdate(requestItem.stockItemId(), supplier.getId())
                    .orElseThrow(() -> new ApiException(ErrorCode.STOCK_NOT_FOUND,
                            "Supplier stock item not found: " + requestItem.stockItemId()));
            if (!stockItem.isActive()) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "Supplier stock item is inactive: " + stockItem.getSku());
            }

            PurchaseOrderItem item = new PurchaseOrderItem();
            item.setStockItem(stockItem);
            item.setQuantity(requestItem.quantity());
            item.setUnitPrice(stockItem.getUnitPrice());
            purchaseOrder.addItem(item);
            total = total.add(stockItem.getUnitPrice().multiply(BigDecimal.valueOf(requestItem.quantity())));
        }

        if (total.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Purchase order total must be positive");
        }
        purchaseOrder.setTotalAmount(total);
        PurchaseOrder saved = purchaseOrderRepository.save(purchaseOrder);

        PurchaseOrderSettlement settlement = new PurchaseOrderSettlement();
        settlement.setPurchaseOrder(saved);
        settlement.setStatus(SettlementStatus.PENDING);
        settlement.setAmount(total);
        settlementRepository.saveAndFlush(settlement);
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> listForDistributor(AuthenticatedUser actor, Pageable pageable) {
        requireDistributor(actor);
        return purchaseOrderRepository.findByBuyerOrganizationId(actor.organizationId(), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public Page<PurchaseOrderResponse> listForManufacturer(AuthenticatedUser actor, Pageable pageable) {
        requireManufacturer(actor);
        return purchaseOrderRepository.findBySupplierOrganizationId(actor.organizationId(), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getForDistributor(AuthenticatedUser actor, UUID id) {
        requireDistributor(actor);
        return toResponse(getAuthorized(id, actor.organizationId(), true));
    }

    @Transactional(readOnly = true)
    public PurchaseOrderResponse getForManufacturer(AuthenticatedUser actor, UUID id) {
        requireManufacturer(actor);
        return toResponse(getAuthorized(id, actor.organizationId(), false));
    }

    @Transactional
    public PurchaseOrderResponse decide(AuthenticatedUser actor, UUID id,
                                        PurchaseOrderDecisionRequest request) {
        requireManufacturerAdmin(actor);
        PurchaseOrder purchaseOrder = getAuthorized(id, actor.organizationId(), false);
        if (purchaseOrder.getStatus() != PurchaseOrderStatus.SUBMITTED) {
            throw new ApiException(ErrorCode.CONFLICT, "Only submitted purchase orders can be reviewed");
        }
        if (request.status() != PurchaseOrderStatus.APPROVED
                && request.status() != PurchaseOrderStatus.REJECTED) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Supplier decision must be APPROVED or REJECTED");
        }
        String note = request.note() == null ? null : request.note().trim();
        if (request.status() == PurchaseOrderStatus.REJECTED && (note == null || note.isBlank())) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "A rejection reason is required");
        }
        purchaseOrder.setStatus(request.status());
        purchaseOrder.setRejectionReason(request.status() == PurchaseOrderStatus.REJECTED ? note : null);
        purchaseOrder.setReviewedBy(userRepository.getReferenceById(actor.userId()));
        purchaseOrder.setReviewedAt(OffsetDateTime.now());
        return toResponse(purchaseOrderRepository.save(purchaseOrder));
    }

    @Transactional(readOnly = true)
    public java.util.List<PurchaseOrderSettlementResponse> settlements(AuthenticatedUser actor, UUID id) {
        PurchaseOrder purchaseOrder = getForEitherParty(actor, id);
        return settlementRepository.findByPurchaseOrderIdOrderByCreatedAtDesc(purchaseOrder.getId())
                .stream().map(this::toSettlementResponse).toList();
    }

    private PurchaseOrder getForEitherParty(AuthenticatedUser actor, UUID id) {
        if (actor == null || actor.organizationId() == null) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Purchase order not found"));
        boolean buyer = actor.role() != null && actor.role().organizationtype() == Organizationtype.DISTRIBUTOR
                && actor.organizationId().equals(purchaseOrder.getBuyerOrganization().getId());
        boolean supplier = actor.role() != null && actor.role().organizationtype() == Organizationtype.MANUFACTURER
                && actor.organizationId().equals(purchaseOrder.getSupplierOrganization().getId());
        if (!buyer && !supplier) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return purchaseOrder;
    }

    private PurchaseOrder getAuthorized(UUID id, UUID organizationId, boolean buyer) {
        PurchaseOrder purchaseOrder = purchaseOrderRepository.findByIdWithItems(id)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Purchase order not found"));
        UUID ownerId = buyer ? purchaseOrder.getBuyerOrganization().getId()
                : purchaseOrder.getSupplierOrganization().getId();
        if (!organizationId.equals(ownerId)) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
        return purchaseOrder;
    }

    private void requireDistributor(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null
                || actor.role() == null || actor.role().organizationtype() != Organizationtype.DISTRIBUTOR) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "A distributor organization identity is required");
        }
    }

    private void requireManufacturer(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null
                || actor.role() == null || actor.role().organizationtype() != Organizationtype.MANUFACTURER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "A manufacturer organization identity is required");
        }
    }

    private void requireManufacturerAdmin(AuthenticatedUser actor) {
        requireManufacturer(actor);
        if (actor.role() != UserRole.MANUFACTURER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED,
                    "Only a manufacturer administrator may review purchase orders");
        }
    }

    private PurchaseOrderResponse toResponse(PurchaseOrder purchaseOrder) {
        return new PurchaseOrderResponse(
                purchaseOrder.getId(), purchaseOrder.getPurchaseOrderNumber(),
                purchaseOrder.getBuyerOrganization().getId(), purchaseOrder.getSupplierOrganization().getId(),
                purchaseOrder.getStatus(), purchaseOrder.getTotalAmount(), purchaseOrder.getCurrency(),
                purchaseOrder.getRejectionReason(), purchaseOrder.getSubmittedAt(),
                purchaseOrder.getReviewedAt(), purchaseOrder.getFulfilledAt(),
                purchaseOrder.getItems().stream().map(item -> new PurchaseOrderItemResponse(
                        item.getId(), item.getStockItem().getId(), item.getStockItem().getSku(),
                        item.getStockItem().getName(), item.getQuantity(), item.getUnitPrice(),
                        item.getLineTotal() != null ? item.getLineTotal() : item.getUnitPrice()
                                .multiply(BigDecimal.valueOf(item.getQuantity())))).toList(),
                settlementRepository.findByPurchaseOrderIdOrderByCreatedAtDesc(purchaseOrder.getId())
                        .stream().map(this::toSettlementResponse).toList());
    }

    private PurchaseOrderSettlementResponse toSettlementResponse(PurchaseOrderSettlement settlement) {
        return new PurchaseOrderSettlementResponse(settlement.getId(), settlement.getStatus(),
                settlement.getAmount(), settlement.getCurrency(), settlement.getProviderReference(),
                settlement.getNote(), settlement.getSettledAt(), settlement.getCreatedAt());
    }
}
