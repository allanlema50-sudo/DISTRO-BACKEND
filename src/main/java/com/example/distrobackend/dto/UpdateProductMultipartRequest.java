package com.example.distrobackend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;

public class UpdateProductMultipartRequest {
    @Size(max = 150) private String name;
    @Size(max = 80) private String category;
    @Size(max = 100) private String brand;
    @Size(max = 30) private String unit;
    @Size(max = 4000) private String description;
    @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) private BigDecimal unitPrice;
    @Size(max = 50) private String sku;
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
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public MultipartFile getImage() { return image; }
    public void setImage(MultipartFile image) { this.image = image; }
    public boolean isEmpty() {
        return name == null && category == null && brand == null && unit == null
                && description == null && unitPrice == null && sku == null
                && (image == null || image.isEmpty());
    }
}
