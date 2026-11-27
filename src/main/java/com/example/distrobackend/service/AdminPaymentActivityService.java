package com.example.distrobackend.service;

import com.example.distrobackend.Domain.enums.CheckoutPaymentStatus;
import com.example.distrobackend.Domain.enums.PaymentProvider;
import com.example.distrobackend.Domain.enums.PaymentStatus;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.AdminPaymentActivityItem;
import com.example.distrobackend.repository.AdminPaymentActivityRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminPaymentActivityService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_PAGE_INDEX = 100_000;
    private static final Duration MAX_RANGE = Duration.ofDays(31);

    private static final Set<String> VALID_STATUSES = Arrays.stream(PaymentStatus.values())
            .map(Enum::name).collect(Collectors.toUnmodifiableSet());
    private static final Set<String> CHECKOUT_STATUSES = Arrays.stream(CheckoutPaymentStatus.values())
            .map(Enum::name).collect(Collectors.toUnmodifiableSet());

    private final AdminPaymentActivityRepository activityRepository;

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Page<AdminPaymentActivityItem> search(AuthenticatedUser actor,
                                                 OffsetDateTime from,
                                                 OffsetDateTime to,
                                                 String provider,
                                                 String providerMode,
                                                 String status,
                                                 UUID organizationId,
                                                 String search,
                                                 int page,
                                                 int size) {
        requireSuperAdmin(actor);
        if (page < 0 || page > MAX_PAGE_INDEX || size < 1 || size > MAX_PAGE_SIZE) {
            throw new ApiException(ErrorCode.BAD_REQUEST,
                    "Page must be between 0 and 100000 and size must be between 1 and 100");
        }
        if (from != null && to != null) {
            if (!to.isAfter(from)) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "to must be later than from");
            }
            if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
                throw new ApiException(ErrorCode.BAD_REQUEST, "The requested date range cannot exceed 31 days");
            }
        }

        String normalizedProvider = enumFilter(provider, PaymentProvider.class, "provider");
        String normalizedMode = normalizeMode(providerMode);
        String normalizedStatus = normalizeStatus(status);
        String normalizedSearch = normalizeSearch(search);
        return activityRepository.search(from, to, normalizedProvider, normalizedMode, normalizedStatus,
                        organizationId, normalizedSearch, PageRequest.of(page, size))
                .map(AdminPaymentActivityItem::from);
    }

    private static String normalizeStatus(String status) {
        if (status == null || status.isBlank()) return null;
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!VALID_STATUSES.contains(normalized) && !CHECKOUT_STATUSES.contains(normalized)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Unsupported payment status filter");
        }
        return normalized;
    }

    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) return null;
        String normalized = mode.trim().toUpperCase(Locale.ROOT);
        if (!normalized.equals("TEST") && !normalized.equals("LIVE")) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "providerMode must be TEST or LIVE");
        }
        return normalized;
    }

    private static String normalizeSearch(String search) {
        if (search == null || search.isBlank()) return null;
        String normalized = search.trim();
        if (normalized.length() > 120) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "search cannot exceed 120 characters");
        }
        return normalized;
    }

    private static <E extends Enum<E>> String enumFilter(String value, Class<E> enumType, String name) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            Enum.valueOf(enumType, normalized);
            return normalized;
        } catch (IllegalArgumentException ex) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "Unsupported " + name + " filter");
        }
    }

    private static void requireSuperAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }
}
