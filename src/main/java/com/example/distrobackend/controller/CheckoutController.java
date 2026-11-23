package com.example.distrobackend.controller;
import com.example.distrobackend.dto.*;
import com.example.distrobackend.security.AuthenticatedUser;
import com.example.distrobackend.service.CentralizedCheckoutService;
import com.example.distrobackend.service.PaystackPaymentService;
import com.example.distrobackend.service.PaystackSettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/checkouts")
@PreAuthorize("hasAnyRole('DISTRIBUTOR_ADMIN','DISTRIBUTOR_STAFF')") @RequiredArgsConstructor
public class CheckoutController {
 private final CentralizedCheckoutService checkoutService;
 private final PaystackPaymentService paystackPaymentService;
 private final PaystackSettlementService paystackSettlementService;
 @org.springframework.web.bind.annotation.PostMapping("/mpesa/callback")
 @PreAuthorize("permitAll()")
 @io.swagger.v3.oas.annotations.security.SecurityRequirements
 public org.springframework.http.ResponseEntity<String> mpesaCallback(@RequestParam UUID paymentId,@RequestHeader(value="X-Mpesa-Callback-Secret",required=false) String secret,@RequestBody com.example.distrobackend.dto.mpesa.MpesaCallbackRequest callback){checkoutService.processMpesaCallback(paymentId,secret,callback);return org.springframework.http.ResponseEntity.ok("{\"ResultCode\":0,\"ResultDesc\":\"Accepted\"}");}
 @org.springframework.web.bind.annotation.PostMapping(value="/paystack/webhook", consumes=org.springframework.http.MediaType.APPLICATION_JSON_VALUE)
 @PreAuthorize("permitAll()")
 @io.swagger.v3.oas.annotations.security.SecurityRequirements
 public org.springframework.http.ResponseEntity<Void> paystackWebhook(
         @RequestHeader(value="Paystack-Signature", required=false) String signature,
         @RequestHeader(value="X-Paystack-Signature", required=false) String legacySignature,
         @RequestBody byte[] rawBody) {
  com.example.distrobackend.service.PaystackPaymentService.Webhook webhook =
          paystackPaymentService.parseAndVerifyWebhook(rawBody, signature, legacySignature);
  if (webhook.event().startsWith("transfer.")) {
   paystackSettlementService.processPaystackWebhook(webhook);
  } else {
   checkoutService.processPaystackWebhook(webhook);
  }
  return org.springframework.http.ResponseEntity.ok().build();
 }
 @PostMapping @ResponseStatus(HttpStatus.CREATED)
 public CheckoutResponse create(@AuthenticationPrincipal AuthenticatedUser actor,@RequestHeader(value="Idempotency-Key",required=true) String key,@Valid @RequestBody CreateCheckoutRequest request){return checkoutService.create(actor,request,key);}
 @GetMapping public Page<CheckoutResponse> history(@AuthenticationPrincipal AuthenticatedUser actor,@PageableDefault(size=20) Pageable pageable){return checkoutService.history(actor,pageable);}
 @GetMapping("/{checkoutId}") public CheckoutResponse get(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable UUID checkoutId){return checkoutService.get(actor,checkoutId);}
 @GetMapping("/{checkoutId}/orders") public List<CheckoutResponse.SupplierOrder> orders(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable UUID checkoutId){return checkoutService.get(actor,checkoutId).supplierOrders();}
 @PostMapping("/{checkoutId}/payment") public CheckoutResponse initiatePayment(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable UUID checkoutId,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody InitiateCheckoutPaymentRequest request){return checkoutService.initiatePayment(actor,checkoutId,request,key);}
 @PostMapping("/{checkoutId}/cancel")
 public CheckoutResponse cancel(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable UUID checkoutId){return checkoutService.cancel(actor,checkoutId);} @GetMapping("/{checkoutId}/payment") public CheckoutResponse.Payment paymentStatus(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable UUID checkoutId){return checkoutService.get(actor,checkoutId).payment();}
 @GetMapping("/{checkoutId}/allocations") public List<CheckoutResponse.Allocation> allocations(@AuthenticationPrincipal AuthenticatedUser actor,@PathVariable UUID checkoutId){return checkoutService.allocations(actor,checkoutId);}
}
