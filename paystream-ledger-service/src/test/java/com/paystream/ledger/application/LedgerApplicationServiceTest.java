package com.paystream.ledger.application;

import com.paystream.common.exception.DomainException;
import com.paystream.ledger.api.dto.request.DoubleEntryRequest;
import com.paystream.ledger.api.dto.request.LedgerEntryRequest;
import com.paystream.ledger.application.port.out.LedgerEntryRepository;
import com.paystream.ledger.application.service.LedgerApplicationService;
import com.paystream.ledger.domain.model.EntryType;
import com.paystream.ledger.domain.model.LedgerEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for LedgerApplicationService.
 * Verifies double-entry invariant enforcement, idempotency, and balance computation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LedgerApplicationService")
class LedgerApplicationServiceTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    private LedgerApplicationService service;

    @BeforeEach
    void setUp() {
        service = new LedgerApplicationService(ledgerEntryRepository);
    }

    // -------------------------------------------------------------------------
    // Double-entry invariant
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Valid double entry (sum=0) persists both entries")
    void createDoubleEntry_validSum_persistsBoth() {
        when(ledgerEntryRepository.existsByReferenceIdAndAccountIdAndEntryType(any(), any(), any()))
                .thenReturn(false);
        when(ledgerEntryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DoubleEntryRequest request = new DoubleEntryRequest(
                "PAY001", "PAYMENT",
                new LedgerEntryRequest("ACCT-A", EntryType.DEBIT, -100000L, "INR", "Debit"),
                new LedgerEntryRequest("ACCT-B", EntryType.CREDIT, 100000L, "INR", "Credit")
        );

        assertThatCode(() -> service.createDoubleEntry(request)).doesNotThrowAnyException();
        verify(ledgerEntryRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("Invalid double entry (sum != 0) throws DomainException and nothing is persisted")
    void createDoubleEntry_invalidSum_throwsAndNothingPersisted() {
        DoubleEntryRequest request = new DoubleEntryRequest(
                "PAY002", "PAYMENT",
                new LedgerEntryRequest("ACCT-A", EntryType.DEBIT, -100000L, "INR", "Debit"),
                new LedgerEntryRequest("ACCT-B", EntryType.CREDIT, 90000L, "INR", "Credit") // sum = -10000
        );

        assertThatThrownBy(() -> service.createDoubleEntry(request))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("Double-entry invariant violated");

        verify(ledgerEntryRepository, never()).save(any());
    }

    @Test
    @DisplayName("Same referenceId twice is idempotent — no new entries written")
    void createDoubleEntry_duplicate_isIdempotent() {
        when(ledgerEntryRepository.existsByReferenceIdAndAccountIdAndEntryType(eq("PAY003"), eq("ACCT-A"), eq("DEBIT")))
                .thenReturn(true);
        when(ledgerEntryRepository.existsByReferenceIdAndAccountIdAndEntryType(eq("PAY003"), eq("ACCT-B"), eq("CREDIT")))
                .thenReturn(true);

        DoubleEntryRequest request = new DoubleEntryRequest(
                "PAY003", "PAYMENT",
                new LedgerEntryRequest("ACCT-A", EntryType.DEBIT, -50000L, "INR", null),
                new LedgerEntryRequest("ACCT-B", EntryType.CREDIT, 50000L, "INR", null)
        );

        assertThatCode(() -> service.createDoubleEntry(request)).doesNotThrowAnyException();
        verify(ledgerEntryRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // Transaction integrity check
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("getTransaction returns integrityValid=true when sum is zero")
    void getTransaction_integrityValid_whenSumIsZero() {
        LedgerEntry debit  = entry("ACCT-A", EntryType.DEBIT,  -100000L, "PAY004");
        LedgerEntry credit = entry("ACCT-B", EntryType.CREDIT,  100000L, "PAY004");
        when(ledgerEntryRepository.findByReferenceId("PAY004")).thenReturn(List.of(debit, credit));

        var response = service.getTransaction("PAY004");

        assertThat(response.integrityValid()).isTrue();
        assertThat(response.entries()).hasSize(2);
    }

    @Test
    @DisplayName("getTransaction returns integrityValid=false when sum is non-zero (corruption)")
    void getTransaction_integrityInvalid_whenSumNotZero() {
        LedgerEntry debit  = entry("ACCT-A", EntryType.DEBIT,  -100000L, "PAY005");
        LedgerEntry credit = entry("ACCT-B", EntryType.CREDIT,   90000L, "PAY005"); // deliberately broken
        when(ledgerEntryRepository.findByReferenceId("PAY005")).thenReturn(List.of(debit, credit));

        var response = service.getTransaction("PAY005");

        assertThat(response.integrityValid()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private LedgerEntry entry(String accountId, EntryType type, long amount, String refId) {
        return new LedgerEntry("ID-" + Math.random(), accountId, type, amount, "INR",
                refId, "PAYMENT", null, Instant.now());
    }
}
