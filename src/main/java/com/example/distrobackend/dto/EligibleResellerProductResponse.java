package com.example.distrobackend.dto;
import com.example.distrobackend.Domain.entity.StockItem;
import java.util.UUID;
public record EligibleResellerProductResponse(UUID manufacturerProductId, String name, String description,
        String imageUrl, String brand, String category, String unit, String manufacturerName,
        boolean alreadyListed, int acceptedQuantity, int availableQuantity) {
    public static EligibleResellerProductResponse from(StockItem p, boolean listed, int accepted) {
        return new EligibleResellerProductResponse(p.getId(), p.getName(), p.getDescription(), null,
                p.getBrand(), p.getCategory(), p.getUnit(), p.getOrganization().getName(), listed, accepted, accepted);
    }
}
