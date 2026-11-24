package com.example.distrobackend.dto;
import com.example.distrobackend.Domain.entity.ResellerListing;
import java.math.BigDecimal;
import java.util.UUID;
public record ResellerListingResponse(UUID id, UUID distributorOrganizationId, UUID manufacturerProductId, BigDecimal retailPrice, String currency, String status, int quantityOnHand, int reservedQuantity, int availableQuantity, String availability, String productName, String description, String imageUrl, String brand, String category, String unit, String manufacturerName, String distributorName) {
 public static ResellerListingResponse from(ResellerListing l) { var p=l.getManufacturerProduct(); int a=l.getAvailableQuantity(); return new ResellerListingResponse(l.getId(),l.getDistributor().getId(),p.getId(),l.getRetailPrice(),"KES",l.getStatus(),l.getQuantityOnHand(),l.getReservedQuantity(),a,a>0?"IN_STOCK":"OUT_OF_STOCK",p.getName(),p.getDescription(),p.isImageAvailable()?"/api/v1/customer/catalog/reseller-listings/"+l.getId()+"/image":null,p.getBrand(),p.getCategory(),p.getUnit(),p.getOrganization().getName(),l.getDistributor().getName()); }
}
