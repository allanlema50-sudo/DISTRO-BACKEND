package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.CheckoutPayment;
import com.example.distrobackend.Domain.enums.CheckoutPaymentStatus;
import com.example.distrobackend.Domain.enums.PaymentProvider;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.ProviderTransactionMatch;
import com.example.distrobackend.dto.ProviderTransactionMatchPage;
import com.example.distrobackend.repository.CheckoutPaymentRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PaystackTransactionMatchingService {
    private final PaystackPaymentService paystack;
    private final CheckoutPaymentRepository paymentRepository;

    @Transactional(readOnly = true)
    public ProviderTransactionMatchPage compare(AuthenticatedUser actor, OffsetDateTime from,
                                                OffsetDateTime to, int page, int perPage) {
        requireSuperAdmin(actor);
        if (from == null || to == null || !from.isBefore(to)
                || Duration.between(from, to).compareTo(Duration.ofDays(31)) > 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Provide a valid Paystack transaction time range no longer than 31 days");
        }
        if (page < 1 || perPage < 1 || perPage > 100) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "page must be positive and perPage must be between 1 and 100");
        }
        PaystackPaymentService.ProviderTransactionPage providerPage =
                paystack.listTransactions(from, to, page, perPage);
        List<String> references = providerPage.transactions().stream()
                .map(PaystackPaymentService.ProviderTransaction::reference).distinct().toList();
        Map<String, CheckoutPayment> localByReference = references.isEmpty() ? Map.of()
                : paymentRepository.findByProviderAndProviderReferenceIn(PaymentProvider.PAYSTACK, references)
                .stream().collect(Collectors.toMap(CheckoutPayment::getProviderReference, payment -> payment));
        List<ProviderTransactionMatch> matches = providerPage.transactions().stream()
                .map(transaction -> match(transaction, localByReference.get(transaction.reference()))).toList();
        return new ProviderTransactionMatchPage(providerPage.page(), providerPage.perPage(),
                providerPage.total(), matches);
    }

    private ProviderTransactionMatch match(PaystackPaymentService.ProviderTransaction provider,
                                           CheckoutPayment local) {
        List<String> issues = new ArrayList<>();
        if (local == null) {
            issues.add("PROVIDER_TRANSACTION_NOT_FOUND_LOCALLY");
        } else {
            if (!"KES".equalsIgnoreCase(provider.currency())
                    || !provider.currency().equalsIgnoreCase(local.getCurrency())) {
                issues.add("CURRENCY_MISMATCH");
            } else if (minorAmount(local.getAmount()) != provider.amountMinor()) {
                issues.add("AMOUNT_MISMATCH");
            }
            String providerStatus = normalizeProviderStatus(provider.status());
            String localStatus = normalizeLocalStatus(local.getStatus());
            if (!providerStatus.equals(localStatus)) issues.add("PAYMENT_STATUS_MISMATCH");
        }
        return new ProviderTransactionMatch(provider.providerId(), provider.reference(), provider.status(),
                provider.amountMinor(), provider.currency(), provider.paidAt(),
                local == null ? null : local.getId(), local == null ? null : local.getStatus().name(),
                local == null ? null : local.getAmount(), local == null ? null : local.getCurrency(), issues);
    }

    private static long minorAmount(BigDecimal amount) {
        try {
            return amount.movePointRight(2).longValueExact();
        } catch (ArithmeticException | NullPointerException ex) {
            return Long.MIN_VALUE;
        }
    }

    private static String normalizeProviderStatus(String status) {
        if (status == null) return "UNKNOWN";
        return switch (status.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "success" -> "SUCCESS";
            case "failed" -> "FAILED";
            case "abandoned" -> "CANCELLED";
            case "reversed" -> "REFUNDED";
            default -> status.trim().toUpperCase(java.util.Locale.ROOT);
        };
    }

    private static String normalizeLocalStatus(CheckoutPaymentStatus status) {
        return switch (status) {
            case SUCCESS, REFUND_PENDING, REFUNDED -> "SUCCESS";
            case FAILED -> "FAILED";
            case CANCELLED, TIMED_OUT -> "CANCELLED";
            case PENDING, UNKNOWN -> status.name();
        };
    }

    private static void requireSuperAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }
}
