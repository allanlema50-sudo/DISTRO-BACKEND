package com.example.distrobackend.controller;
import com.example.distrobackend.dto.*;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.ResellerListingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/distributor/reseller-listings") @RequiredArgsConstructor
@PreAuthorize("hasAnyRole('DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF') and @tenantAccess.hasOrganization(authentication)")
public class ResellerListingController {
 private final ResellerListingService service;
 @GetMapping public List<ResellerListingResponse> list(@AuthenticationPrincipal AuthenticatedUser actor){return service.list(actor);}
 @PostMapping @ResponseStatus(HttpStatus.CREATED) public ResellerListingResponse create(@AuthenticationPrincipal AuthenticatedUser actor,@Valid @RequestBody ResellerListingRequest request){return service.create(actor,request);}
 @GetMapping("/eligible-products") public List<EligibleResellerProductResponse> eligible(@AuthenticationPrincipal AuthenticatedUser actor){return service.eligible(actor);}
 @GetMapping("/{id}") public ResellerListingResponse get(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable UUID id){return service.get(actor,id);}
 @PatchMapping("/{id}/price") public ResellerListingResponse price(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable UUID id,@Valid @RequestBody RetailPriceUpdateRequest request){return service.updatePrice(actor,id,request);}
 @PostMapping("/{id}/pause") public ResellerListingResponse pause(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable UUID id){return service.pause(actor,id);}
 @PostMapping("/{id}/resume") public ResellerListingResponse resume(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable UUID id){return service.resume(actor,id);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void archive(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable UUID id){service.archive(actor,id);}
}
