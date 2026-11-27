package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.entity.PlatformFeeSetting;
import com.example.distrobackend.Domain.entity.PurchaseOrder;
import com.example.distrobackend.Domain.entity.PurchaseOrderSettlement;
import com.example.distrobackend.Domain.enums.FeePayer;
import com.example.distrobackend.Domain.enums.PlatformFeeType;
import com.example.distrobackend.Domain.enums.UserRole;
import com.example.distrobackend.Exception.ApiException;
import com.example.distrobackend.Exception.ErrorCode;
import com.example.distrobackend.dto.PlatformFeeReportResponse;
import com.example.distrobackend.dto.PlatformFeeSettingResponse;
import com.example.distrobackend.dto.UpdatePlatformFeeRequest;
import com.example.distrobackend.repository.LedgerEntryRepository;
import com.example.distrobackend.repository.PlatformFeeSettingRepository;
import com.example.distrobackend.repository.UserRepository;
import com.example.distrobackend.security.AuthenticatedUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * Platform fee on settled purchase orders. The fee is a receivable from the
 * paying organization (the supplier by default) and is never taken out of the
 * manufacturer's payable. It is zero until a super admin configures it.
 */
@Service
@RequiredArgsConstructor
public class PlatformFeeService {

    private static final ZoneId NAIROBI = ZoneId.of("Africa/Nairobi");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final long MAX_REPORT_DAYS = 366;

    private final PlatformFeeSettingRepository settingRepository;
    private final FinancialLedgerService ledgerService;
    private final LedgerEntryRepository entryRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public PlatformFeeSettingResponse get(AuthenticatedUser actor) {
        requireSuperAdmin(actor);
        return PlatformFeeSettingResponse.from(currentSetting());
    }

    @Transactional
    public PlatformFeeSettingResponse update(AuthenticatedUser actor, UpdatePlatformFeeRequest request) {
        requireSuperAdmin(actor);
        PlatformFeeSetting setting = settingRepository.findByScope(PlatformFeeSetting.PURCHASE_ORDER_SCOPE)
                .orElseGet(PlatformFeeSetting::new);
        if (request.expectedVersion() != null && setting.getId() != null
                && setting.getVersion() != request.expectedVersion()) {
            throw new ApiException(ErrorCode.CONFLICT, "The fee settings were changed by someone else; reload and retry");
        }
        BigDecimal value = request.feeValue();
        if (value.scale() > 2) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "feeValue must have at most two decimal places");
        }
        if (request.feeType() == PlatformFeeType.PERCENT && value.compareTo(HUNDRED) > 0) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "A percentage fee cannot exceed 100");
        }
        setting.setScope(PlatformFeeSetting.PURCHASE_ORDER_SCOPE);
        setting.setFeeType(request.feeType());
        setting.setFeeValue(value.setScale(2, RoundingMode.UNNECESSARY));
        setting.setCurrency(request.currency() == null || request.currency().isBlank()
                ? "KES" : request.currency().trim().toUpperCase(Locale.ROOT));
        setting.setFeePayer(request.feePayer() == null ? FeePayer.SUPPLIER : request.feePayer());
        setting.setUpdatedBy(userRepository.getReferenceById(actor.userId()));
        return PlatformFeeSettingResponse.from(settingRepository.save(setting));
    }

    /**
     * Evaluates and accrues the fee once, when a settlement becomes SETTLED by
     * any method. Stores the amount on the settlement for audit (zero is
     * recorded too, so a settlement is never evaluated twice) and posts a
     * receivable only when the fee is positive. The caller saves the settlement.
     */
    @Transactional
    public void accrueOnSettlement(PurchaseOrderSettlement settlement) {
        if (settlement.getPlatformFeeAmount() != null) {
            return;
        }
        PurchaseOrder order = settlement.getPurchaseOrder();
        PlatformFeeSetting setting = settingRepository.findByScope(PlatformFeeSetting.PURCHASE_ORDER_SCOPE)
                .orElse(null);
        BigDecimal fee = computeFee(setting, settlement.effectiveTotalDue(), settlement.getCurrency());
        FeePayer payer = setting == null ? FeePayer.SUPPLIER : setting.getFeePayer();
        settlement.setPlatformFeeAmount(fee);
        settlement.setPlatformFeePayer(payer);
        if (fee.signum() > 0) {
            Organization payerOrganization = payer == FeePayer.BUYER
                    ? order.getBuyerOrganization() : order.getSupplierOrganization();
            ledgerService.accruePlatformFeeReceivable(FinancialLedgerService.PURCHASE_ORDER_FEE,
                    settlement.getId(), payerOrganization, fee, settlement.getCurrency(),
                    "Platform fee on purchase order " + order.getPurchaseOrderNumber());
        }
    }

    static BigDecimal computeFee(PlatformFeeSetting setting, BigDecimal total, String currency) {
        BigDecimal zero = BigDecimal.ZERO.setScale(2);
        if (setting == null || total == null || total.signum() <= 0 || setting.getFeeValue().signum() <= 0) {
            return zero;
        }
        BigDecimal fee;
        if (setting.getFeeType() == PlatformFeeType.PERCENT) {
            fee = total.multiply(setting.getFeeValue()).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        } else if (setting.getCurrency().equalsIgnoreCase(currency)) {
            fee = setting.getFeeValue().setScale(2, RoundingMode.HALF_UP);
        } else {
            // A flat amount is only meaningful in its own currency.
            return zero;
        }
        return fee.min(total.setScale(2, RoundingMode.HALF_UP));
    }

    @Transactional(readOnly = true)
    public PlatformFeeReportResponse report(AuthenticatedUser actor, LocalDate from, LocalDate to, String currency) {
        requireSuperAdmin(actor);
        LocalDate end = to == null ? LocalDate.now(NAIROBI) : to;
        LocalDate start = from == null ? end.withDayOfMonth(1) : from;
        if (start.isAfter(end)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "from must not be after to");
        }
        if (start.plusDays(MAX_REPORT_DAYS).isBefore(end)) {
            throw new ApiException(ErrorCode.BAD_REQUEST, "The report period cannot exceed 366 days");
        }
        String normalizedCurrency = currency == null || currency.isBlank()
                ? null : currency.trim().toUpperCase(Locale.ROOT);
        List<PlatformFeeReportResponse.Row> rows = entryRepository.platformFeeTotals(
                        start.atStartOfDay(NAIROBI).toOffsetDateTime(),
                        end.plusDays(1).atStartOfDay(NAIROBI).toOffsetDateTime(),
                        normalizedCurrency).stream()
                .map(row -> new PlatformFeeReportResponse.Row(row.getOrganizationId(), row.getOrganizationName(),
                        row.getCurrency(), row.getFeeCount() == null ? 0 : row.getFeeCount(), row.getTotalFees()))
                .toList();
        BigDecimal total = rows.stream().map(PlatformFeeReportResponse.Row::totalFees)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new PlatformFeeReportResponse(start, end, normalizedCurrency, rows, total);
    }

    private PlatformFeeSetting currentSetting() {
        return settingRepository.findByScope(PlatformFeeSetting.PURCHASE_ORDER_SCOPE).orElseGet(PlatformFeeSetting::new);
    }

    private static void requireSuperAdmin(AuthenticatedUser actor) {
        if (actor == null || actor.role() != UserRole.SUPER_ADMIN) {
            throw new ApiException(ErrorCode.ACCESS_DENIED);
        }
    }
}
