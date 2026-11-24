package com.example.distrobackend.controller;
import com.example.distrobackend.dto.ResellerListingResponse;
import com.example.distrobackend.service.ResellerListingService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/customer/catalog/reseller-listings") @RequiredArgsConstructor
@PreAuthorize("hasRole('CUSTOMER')")
public class CustomerResellerCatalogController {
 private final ResellerListingService service;
 @GetMapping public Page<ResellerListingResponse> search(@RequestParam(defaultValue="") String search,
     @RequestParam(defaultValue="") String category,@RequestParam(defaultValue="") String brand,
     @RequestParam(required=false) UUID distributorOrganizationId,@RequestParam(defaultValue="false") boolean availableOnly,
     @PageableDefault(size=20) Pageable pageable){return service.catalog(search,category,brand,distributorOrganizationId,availableOnly,pageable);}
 @GetMapping("/{id}") public ResellerListingResponse get(@PathVariable UUID id){return service.getVisible(id);}
 @GetMapping(value="/{id}/image",produces={MediaType.IMAGE_JPEG_VALUE,MediaType.IMAGE_PNG_VALUE,"image/webp"})
 public ResponseEntity<byte[]> image(@PathVariable UUID id){var image=service.image(id);return ResponseEntity.ok().contentType(MediaType.parseMediaType(image.getContentType())).body(image.getContent());}
}
