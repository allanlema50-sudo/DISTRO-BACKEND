package com.example.distrobackend.dto;

import com.example.distrobackend.Domain.enums.ProductPublicationStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

public class CreateProductMultipartRequest {
    @NotBlank @Size(max = 150)
    private String name;
    @Size(max = 80)
    private String category;
    @Size(max = 100)
    private String brand;
    @Size(max = 30)
    private String unit;
    @Size(max = 4000)
    private String description;
    @NotBlank @Size(max = 50)
    private String sku;
    @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2)
    private BigDecimal unitPrice;
    @Min(0)
    private int initialQuantity;
    @Min(0)
    private int reorderThreshold;
    @NotNull
    private ProductPublicationStatus publicationStatus = ProductPublicationStatus.DRAFT;
    @NotNull
    private MultipartFile image;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public int getInitialQuantity() { return initialQuantity; }
    public void setInitialQuantity(int initialQuantity) { this.initialQuantity = initialQuantity; }
    public int getReorderThreshold() { return reorderThreshold; }
    public void setReorderThreshold(int reorderThreshold) { this.reorderThreshold = reorderThreshold; }
    public ProductPublicationStatus getPublicationStatus() { return publicationStatus; }
    public void setPublicationStatus(ProductPublicationStatus publicationStatus) { this.publicationStatus = publicationStatus; }
    public MultipartFile getImage() { return image; }
    public void setImage(MultipartFile image) { this.image = image; }
}
