package com.example.distrobackend.service;

import com.example.distrobackend.Domain.entity.LedgerAccount;
import com.example.distrobackend.Domain.entity.LedgerJournal;
import com.example.distrobackend.Domain.entity.Organization;
import com.example.distrobackend.Domain.enums.LedgerEntryDirection;
import com.example.distrobackend.Domain.enums.Organizationtype;
import com.example.distrobackend.repository.LedgerAccountRepository;
import com.example.distrobackend.repository.LedgerJournalRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FinancialLedgerServiceTest {
    @Mock private LedgerAccountRepository accountRepository;
    @Mock private LedgerJournalRepository journalRepository;
    @Mock private CommissionRuleService commissionRuleService;
    @Mock private PostgresTransactionLockService transactionLockService;

    @Test
    void recordsABalancedCaptureJournalAndCommission() {
        FinancialLedgerService service = new FinancialLedgerService(
                accountRepository, journalRepository, commissionRuleService, transactionLockService);
        Organization distributor = new Organization();
        distributor.setId(UUID.randomUUID());
        distributor.setName("Distributor");
        distributor.setType(Organizationtype.DISTRIBUTOR);
        when(journalRepository.findBySourceTypeAndSourceIdAndEventType(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(accountRepository.findPlatformAccount(any(), eq("KES"))).thenReturn(Optional.empty());
        when(accountRepository.findOrganizationAccount(any(), eq("KES"), eq(distributor.getId())))
                .thenReturn(Optional.empty());
        when(accountRepository.save(any(LedgerAccount.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(commissionRuleService.resolve(eq(Organizationtype.DISTRIBUTOR), eq("KES"), any()))
                .thenReturn(new CommissionRuleService.CommissionDecision(
                        UUID.randomUUID(), new BigDecimal("5.00"), BigDecimal.ZERO, "KES"));

        FinancialLedgerService.FinancialBreakdown result = service.recordCapturedPayment(
                "CUSTOMER_ORDER_PAYMENT", UUID.randomUUID(), distributor,
                new BigDecimal("100.00"), BigDecimal.ZERO, "KES", false);

        assertThat(result.commission()).isEqualByComparingTo("5.00");
        assertThat(result.supplierAmount()).isEqualByComparingTo("95.00");
        ArgumentCaptor<LedgerJournal> captor = ArgumentCaptor.forClass(LedgerJournal.class);
        verify(journalRepository).save(captor.capture());
        LedgerJournal journal = captor.getValue();
        BigDecimal debits = journal.getEntries().stream()
                .filter(entry -> entry.getDirection() == LedgerEntryDirection.DEBIT)
                .map(entry -> entry.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = journal.getEntries().stream()
                .filter(entry -> entry.getDirection() == LedgerEntryDirection.CREDIT)
                .map(entry -> entry.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(debits).isEqualByComparingTo(credits);
        assertThat(journal.getEntries()).hasSize(3);
    }

    @Test
    void doesNotCreateASecondJournalForTheSamePaymentEvent() {
        FinancialLedgerService service = new FinancialLedgerService(
                accountRepository, journalRepository, commissionRuleService, transactionLockService);
        Organization distributor = new Organization();
        distributor.setId(UUID.randomUUID());
        distributor.setType(Organizationtype.DISTRIBUTOR);
        UUID sourceId = UUID.randomUUID();
        when(journalRepository.findBySourceTypeAndSourceIdAndEventType("PAYMENT", sourceId, "PAYMENT_CAPTURED"))
                .thenReturn(Optional.of(new LedgerJournal()));
        when(commissionRuleService.resolve(eq(Organizationtype.DISTRIBUTOR), eq("KES"), any()))
                .thenReturn(new CommissionRuleService.CommissionDecision(
                        null, new BigDecimal("5.00"), BigDecimal.ZERO, "KES"));

        service.recordCapturedPayment("PAYMENT", sourceId, distributor,
                new BigDecimal("100.00"), BigDecimal.ZERO, "KES", false);

        verify(journalRepository, never()).save(any(LedgerJournal.class));
    }
}
