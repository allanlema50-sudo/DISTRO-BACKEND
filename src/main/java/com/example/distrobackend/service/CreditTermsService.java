package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.DistributorCreditTerms;
import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.PlatformCreditDefault;
import com.example.distrobackend.Domain.entity.PurchaseOrder;
import com.example.distrobackend.Domain.entity.PurchaseOrderSettlementPayment;
import com.example.distrobackend.Domain.enums.CreditTermStatus;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.CreditDefaultResponse;
import com.example.distrobackend.dto.CreditStatementResponse;
import com.example.distrobackend.dto.DistributorCreditResponse;
import com.example.distrobackend.dto.SettlementPaymentResponse;
import com.example.distrobackend.dto.UpdateCreditDefaultRequest;
import com.example.distrobackend.dto.UpdateCreditTermsRequest;
import com.example.distrobackend.repository.DistributorCreditTermsRepository;
import com.example.distrobackend.repository.OrganizationRepository;
import com.example.distrobackend.repository.PlatformCreditDefaultRepository;
import com.example.distrobackend.repository.PurchaseOrderRepository;
import com.example.distrobackend.repository.PurchaseOrderSettlementPaymentRepository;
import com.example.distrobackend.repository.PurchaseOrderSettlementRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Credit a manufacturer extends to a distributor. The relationship row decides
 * the limit and term. With no row, the platform default for the currency
 * applies only if one exists with a limit above zero; otherwise credit is
 * refused. Outstanding credit is calculated from orders and settlements.
 */
@Service
@RequiredArgsConstructor
public class CreditTermsService {

    private static final int STATEMENT_LIMIT = 200;

    private final DistributorCreditTermsRepository termsRepository;
    private final PlatformCreditDefaultRepository defaultRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderSettlementRepository settlementRepository;
    private final PurchaseOrderSettlementPaymentRepository paymentRepository;
    private final OrganizationRepository organizationRepository;
    private final UserRepository userRepository;

    /** Terms that apply to one new credit order, resolved while the pair is locked. */
    public record CreditContext(BigDecimal limit, int termDays, Long termsVersion, String currency) { }

    /**
     * Locks the buyer organization and the pair's terms row until the caller's
     * transaction ends, then resolves the terms and blocks the order if any
     * earlier credit order for the pair is overdue. Concurrent credit orders
     * from the same distributor therefore queue here, so each one sees the
     * others' outstanding amounts when it checks the limit.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public CreditContext lockAndResolve(Organization buyer, Organization supplier, String currency) {
        organizationRepository.findByIdForUpdate(buyer.getId());
        Optional<DistributorCreditTerms> row = termsRepository.findForUpdate(supplier.getId(), buyer.getId());
        CreditContext context = resolve(row.orElse(null), currency);
        long overdue = purchaseOrderRepository.countOverdueCreditOrders(
                supplier.getId(), buyer.getId(), OffsetDateTime.now());
        if (overdue > 0) {
            throw new ApiException(ErrorCode.CREDIT_OVERDUE, overdue == 1
                    ? "A credit order is overdue. Pay it before placing a new credit order."
                    : overdue + " credit orders are overdue. Pay them before placing a new credit order.");
        }
        return context;
    }

    /** Rejects the order when outstanding credit plus its total would exceed the limit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireWithinLimit(CreditContext context, Organization buyer, Organization supplier,
                                   BigDecimal orderTotal) {
        BigDecimal outstanding = zeroIfNull(purchaseOrderRepository.outstandingCredit(
                supplier.getId(), buyer.getId(), context.currency()));
        if (outstanding.add(orderTotal).compareTo(context.limit()) > 0) {
            throw new ApiException(ErrorCode.CREDIT_LIMIT_EXCEEDED, "This order of " + orderTotal.toPlainString()
                    + " " + context.currency() + " would take outstanding credit from "
                    + outstanding.toPlainString() + " above the " + context.limit().toPlainString()
                    + " limit. Pay down the balance or ask the manufacturer to raise the limit.");
        }
    }

    private CreditContext resolve(DistributorCreditTerms row, String currency) {
        String normalized = normalizeCurrency(currency);
        if (row != null) {
            if (row.getStatus() == CreditTermStatus.SUSPENDED) {
                throw new ApiException(ErrorCode.CREDIT_NOT_AVAILABLE,
                        "Credit is suspended for this distributor. Contact the manufacturer.");
            }
            if (!row.getCurrency().equalsIgnoreCase(normalized)) {
                throw new ApiException(ErrorCode.CREDIT_NOT_AVAILABLE,
                        "The credit terms with this manufacturer are in " + row.getCurrency());
            }
            return new CreditContext(row.getCreditLimit(), row.getTermDays(), row.getVersion(), normalized);
        }
        PlatformCreditDefault fallback = defaultRepository.findById(normalized).orElse(null);
        if (fallback == null || fallback.getDefaultLimit().signum() <= 0) {
            throw new ApiException(ErrorCode.CREDIT_NOT_AVAILABLE,
                    "The manufacturer has not set credit terms for this distributor, and no platform default "
                            + "applies. Ask the manufacturer to set a credit limit, or pay now.");
        }
        return new CreditContext(fallback.getDefaultLimit(), fallback.getDefaultTermDays(), null, normalized);
    }

    @Transactional(readOnly = true)
    public DistributorCreditResponse getCredit(AuthenticatedUser actor, UUID distributorId) {
        requireManufacturer(actor);
        Organization distributor = requireDistributor(distributorId);
        return describe(actor.organizationId(), distributor, "KES");
    }

    @Transactional
    public DistributorCreditResponse putCredit(AuthenticatedUser actor, UUID distributorId,
                                               UpdateCreditTermsRequest request) {
        requireManufacturerAdmin(actor);
        Organization distributor = requireDistributor(distributorId);
        if (request.creditLimit().scale() > 2) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "creditLimit must have at most two decimal places");
        }
        String currency = normalizeCurrency(request.currency());
        DistributorCreditTerms terms = termsRepository
                .findByManufacturerOrganization_IdAndDistributorOrganization_Id(actor.organizationId(), distributorId)
                .orElse(null);
        if (terms == null) {
            terms = new DistributorCreditTerms();
            terms.setManufacturerOrganization(organizationRepository.getReferenceById(actor.organizationId()));
            terms.setDistributorOrganization(distributor);
        } else if (request.expectedVersion() != null && terms.getVersion() != request.expectedVersion()) {
            throw new ApiException(ErrorCode.CONFLICT, "These credit terms were changed by someone else; reload and retry");
        }
        terms.setCurrency(currency);
        terms.setCreditLimit(request.creditLimit().setScale(2, RoundingMode.UNNECESSARY));
        terms.setTermDays(request.termDays());
        terms.setStatus(request.status() == null ? CreditTermStatus.ACTIVE : request.status());
        terms.setSetBy(userRepository.getReferenceById(actor.userId()));
        termsRepository.saveAndFlush(terms);
        return describe(actor.organizationId(), distributor, currency);
    }

    @Transactional(readOnly = true)
    public CreditStatementResponse statement(AuthenticatedUser actor, UUID distributorId) {
        requireManufacturer(actor);
        Organization distributor = requireDistributor(distributorId);
        DistributorCreditResponse credit = describe(actor.organizationId(), distributor, "KES");
        OffsetDateTime now = OffsetDateTime.now();
        List<PurchaseOrder> orders = purchaseOrderRepository.findCreditOrdersForPair(
                actor.organizationId(), distributorId, PageRequest.of(0, STATEMENT_LIMIT));
        List<CreditStatementResponse.Line> lines = orders.stream().map(order -> {
            BigDecimal paid = zeroIfNull(settlementRepository.sumAmountPaid(order.getId()));
            BigDecimal outstanding = order.getTotalAmount().subtract(paid).max(BigDecimal.ZERO);
            boolean live = order.getStatus() == com.example.distrobackend.Domain.enums.PurchaseOrderStatus.APPROVED
                    || order.getStatus() == com.example.distrobackend.Domain.enums.PurchaseOrderStatus.FULFILLED;
            boolean overdue = live && outstanding.signum() > 0 && order.getDueAt() != null
                    && order.getDueAt().isBefore(now);
            List<SettlementPaymentResponse> payments = paymentRepository
                    .findBySettlement_PurchaseOrder_IdOrderBySubmittedAtAsc(order.getId()).stream()
                    .map(CreditTermsService::toPaymentResponse).toList();
            return new CreditStatementResponse.Line(order.getId(), order.getPurchaseOrderNumber(),
                    order.getStatus(), order.getTotalAmount(), paid, outstanding, order.getCreatedAt(),
                    order.getDueAt(), overdue, payments);
        }).toList();
        return new CreditStatementResponse(credit, lines);
    }

    @Transactional(readOnly = true)
    public List<CreditDefaultResponse> listDefaults(AuthenticatedUser actor) {
        requireSuperAdmin(actor);
        return defaultRepository.findAll().stream().map(CreditDefaultResponse::from).toList();
    }

    @Transactional
    public CreditDefaultResponse putDefault(AuthenticatedUser actor, UpdateCreditDefaultRequest request) {
        requireSuperAdmin(actor);
        if (request.defaultLimit().scale() > 2) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "defaultLimit must have at most two decimal places");
        }
        String currency = normalizeCurrency(request.currency());
        PlatformCreditDefault value = defaultRepository.findById(currency).orElse(null);
        if (value == null) {
            value = new PlatformCreditDefault();
            value.setCurrency(currency);
        } else if (request.expectedVersion() != null && value.getVersion() != request.expectedVersion()) {
            throw new ApiException(ErrorCode.CONFLICT, "The credit defaults were changed by someone else; reload and retry");
        }
        value.setDefaultLimit(request.defaultLimit().setScale(2, RoundingMode.UNNECESSARY));
        value.setDefaultTermDays(request.defaultTermDays());
        value.setUpdatedBy(userRepository.getReferenceById(actor.userId()));
        return CreditDefaultResponse.from(defaultRepository.saveAndFlush(value));
    }

    private DistributorCreditResponse describe(UUID manufacturerId, Organization distributor, String currency) {
        DistributorCreditTerms row = termsRepository
                .findByManufacturerOrganization_IdAndDistributorOrganization_Id(manufacturerId, distributor.getId())
                .orElse(null);
        String effectiveCurrency = row != null ? row.getCurrency() : currency;
        String source = "NONE";
        BigDecimal effectiveLimit = null;
        Integer effectiveDays = null;
        if (row != null) {
            if (row.getStatus() == CreditTermStatus.ACTIVE) {
                source = "RELATIONSHIP";
                effectiveLimit = row.getCreditLimit();
                effectiveDays = row.getTermDays();
            }
        } else {
            PlatformCreditDefault fallback = defaultRepository.findById(effectiveCurrency).orElse(null);
            if (fallback != null && fallback.getDefaultLimit().signum() > 0) {
                source = "PLATFORM_DEFAULT";
                effectiveLimit = fallback.getDefaultLimit();
                effectiveDays = fallback.getDefaultTermDays();
            }
        }
        BigDecimal outstanding = zeroIfNull(purchaseOrderRepository.outstandingCredit(
                manufacturerId, distributor.getId(), effectiveCurrency));
        BigDecimal available = effectiveLimit == null ? BigDecimal.ZERO
                : effectiveLimit.subtract(outstanding).max(BigDecimal.ZERO);
        long overdue = purchaseOrderRepository.countOverdueCreditOrders(
                manufacturerId, distributor.getId(), OffsetDateTime.now());
        return new DistributorCreditResponse(distributor.getId(), distributor.getName(), effectiveCurrency,
                row != null, row == null ? null : row.getCreditLimit(), row == null ? null : row.getTermDays(),
                row == null ? null : row.getStatus(), row == null ? null : row.getVersion(),
                source, effectiveLimit, effectiveDays, outstanding, available, overdue);
    }

    static SettlementPaymentResponse toPaymentResponse(PurchaseOrderSettlementPayment payment) {
        return new SettlementPaymentResponse(payment.getId(), payment.getMethod(), payment.getPayerReference(),
                payment.getAmount(), payment.getStatus(),
                payment.getProofDocument() == null ? null : payment.getProofDocument().getId(),
                payment.getSubmittedAt(), payment.getConfirmedAt());
    }

    private Organization requireDistributor(UUID distributorId) {
        Organization distributor = organizationRepository.findById(distributorId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Distributor not found"));
        if (distributor.getType() != Organizationtype.DISTRIBUTOR) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "The organization is not a distributor");
        }
        return distributor;
    }

    private static String normalizeCurrency(String currency) {
        return currency == null || currency.isBlank() ? "KES" : currency.trim().toUpperCase(Locale.ROOT);
    }

    private static BigDecimal zeroIfNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static void requireManufacturer(AuthenticatedUser actor) {
        if (actor == null || actor.organizationId() == null || actor.role() == null
                || actor.role().organizationtype() != Organizationtype.MANUFACTURER) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "A manufacturer organization identity is required");
        }
    }

    private static void requireManufacturerAdmin(AuthenticatedUser actor) {
        requireManufacturer(actor);
        if (actor.role() != UserRole.MANUFACTURER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED, "Only a manufacturer administrator may set credit terms");
        }
    }

    private static void requireSuperAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }
}
