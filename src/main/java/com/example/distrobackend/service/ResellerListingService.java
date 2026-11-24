package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.*;
import com.example.distrobackend.Domain.enums.CheckoutPaymentStatus;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.PurchaseOrderStatus;
import com.example.distrobackend.Domain.enums.ProductPublicationStatus;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.*;
import com.example.distrobackend.repository.*;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service @RequiredArgsConstructor
public class ResellerListingService {
 private final ResellerListingRepository listings;
 private final StockItemRepository products;
 private final OrganizationRepository organizations;
 private final PurchaseOrderRepository purchaseOrders;
 private final PurchaseOrderShipmentRepository receipts;
 private final ProductImageRepository productImages;

 @Transactional(readOnly=true)
 public List<ResellerListingResponse> list(AuthenticatedUser actor) { return listings.findByDistributor_Id(actor.organizationId()).stream().map(ResellerListingResponse::from).toList(); }

 @Transactional(readOnly=true)
 public ResellerListingResponse get(AuthenticatedUser actor, UUID listingId) { return ResellerListingResponse.from(owned(actor, listingId)); }

 @Transactional(readOnly=true)
 public ResellerListingResponse getVisible(UUID listingId) { return ResellerListingResponse.from(listings.findVisibleById(listingId).orElseThrow(()->new ApiException(ErrorCode.NOT_FOUND,"Reseller listing not found"))); }

 @Transactional(readOnly=true)
 public ProductImage image(UUID listingId) { var l=listings.findVisibleById(listingId).orElseThrow(()->new ApiException(ErrorCode.NOT_FOUND,"Reseller listing not found")); return productImages.findById(l.getManufacturerProduct().getId()).orElseThrow(()->new ApiException(ErrorCode.NOT_FOUND,"Product image not found")); }

 @Transactional(readOnly=true)
 public org.springframework.data.domain.Page<ResellerListingResponse> catalog(String search, String category, String brand, UUID sellerId, boolean availableOnly, org.springframework.data.domain.Pageable pageable) {
  return listings.searchCatalog(clean(search), clean(category), clean(brand), sellerId, availableOnly, pageable).map(ResellerListingResponse::from);
 }

 @Transactional(readOnly=true)
 public List<EligibleResellerProductResponse> eligible(AuthenticatedUser actor) {
  Map<UUID, StockItem> productsById = new LinkedHashMap<>();
  purchaseOrders.findByBuyerOrganizationId(actor.organizationId(),org.springframework.data.domain.Pageable.unpaged()).getContent().stream()
   .filter(this::paid).flatMap(po->po.getItems().stream()).map(PurchaseOrderItem::getStockItem)
   .filter(p -> p.getOrganization().getType()==Organizationtype.MANUFACTURER && p.getSourceStockItem()==null)
   .forEach(p -> productsById.putIfAbsent(p.getId(),p));
  return productsById.values().stream().map(p -> {
   int accepted=Math.toIntExact(Optional.ofNullable(receipts.sumAcceptedQuantityForDistributorProduct(actor.organizationId(),p.getId())).orElse(0L));
   return EligibleResellerProductResponse.from(p, listings.findByDistributor_IdAndManufacturerProduct_Id(actor.organizationId(),p.getId()).isPresent(), accepted);
  }).toList();
 }

 @Transactional
 public ResellerListingResponse create(AuthenticatedUser actor, ResellerListingRequest request) {
  StockItem product=products.findByIdForUpdate(request.manufacturerProductId()).orElseThrow(()->new ApiException(ErrorCode.STOCK_ITEM_NOT_FOUND,"Manufacturer product not found"));
  if(product.getSourceStockItem()!=null || product.getOrganization().getType()!=Organizationtype.MANUFACTURER || !product.isActive() || product.getPublicationStatus()!=ProductPublicationStatus.PUBLISHED) throw new ApiException(ErrorCode.BAD_REQUEST,"A published canonical Manufacturer product is required");
  boolean eligible=purchaseOrders.findByBuyerOrganizationId(actor.organizationId(),org.springframework.data.domain.Pageable.unpaged()).getContent().stream()
   .filter(this::paid)
   .flatMap(po->po.getItems().stream()).anyMatch(line->line.getStockItem().getId().equals(product.getId()));
  if(!eligible) throw new ApiException(ErrorCode.ACCESS_DENIED,"A confirmed paid wholesale purchase is required before listing this product");
  ResellerListing listing=listings.findByDistributor_IdAndManufacturerProduct_Id(actor.organizationId(),product.getId()).orElseGet(ResellerListing::new);
  listing.setDistributor(organizations.getReferenceById(actor.organizationId())); listing.setManufacturerProduct(product); listing.setRetailPrice(request.retailPrice()); listing.setStatus("ACTIVE");
  if (listing.getId()==null) listing.setQuantityOnHand(Math.toIntExact(Optional.ofNullable(receipts.sumAcceptedQuantityForDistributorProduct(actor.organizationId(),product.getId())).orElse(0L)));
  return ResellerListingResponse.from(listings.save(listing));
 }

 @Transactional
 public ResellerListingResponse updatePrice(AuthenticatedUser actor, UUID id, RetailPriceUpdateRequest request) { var l=owned(actor,id); l.setRetailPrice(request.retailPrice()); return ResellerListingResponse.from(l); }
 @Transactional
 public ResellerListingResponse pause(AuthenticatedUser actor, UUID id) { var l=owned(actor,id); l.setStatus("PAUSED"); return ResellerListingResponse.from(l); }
 @Transactional
 public ResellerListingResponse resume(AuthenticatedUser actor, UUID id) { var l=owned(actor,id); if(l.getManufacturerProduct().getPublicationStatus()!=com.example.distrobackend.Domain.enums.ProductPublicationStatus.PUBLISHED) throw new ApiException(ErrorCode.CONFLICT,"The Manufacturer product is not published"); l.setStatus("ACTIVE"); return ResellerListingResponse.from(l); }
 @Transactional
 public void archive(AuthenticatedUser actor, UUID id) { var l=owned(actor,id); l.setStatus("ARCHIVED"); }

 private ResellerListing owned(AuthenticatedUser actor, UUID id) { return listings.findByIdAndDistributor_Id(id,actor.organizationId()).orElseThrow(()->new ApiException(ErrorCode.NOT_FOUND,"Reseller listing not found")); }
 private boolean paid(PurchaseOrder po) { return po.getStatus()!=PurchaseOrderStatus.CANCELLED && po.getStatus()!=PurchaseOrderStatus.REJECTED && po.getCheckout()!=null && po.getCheckout().getPayment()!=null && po.getCheckout().getPayment().getStatus()==CheckoutPaymentStatus.SUCCESS; }
 private String clean(String value) { return value==null?"":value.trim(); }
}
