package com.paystream.ledger.application.service;

import com.paystream.common.constant.ErrorCode;
import com.paystream.common.event.wallet.WalletCreditedEvent;
import com.paystream.common.event.wallet.WalletDebitedEvent;
import com.paystream.common.exception.DomainException;
import com.paystream.common.exception.ResourceNotFoundException;
import com.paystream.common.util.IdGenerator;
import com.paystream.ledger.api.dto.request.DoubleEntryRequest;
import com.paystream.ledger.api.dto.response.BalanceResponse;
import com.paystream.ledger.api.dto.response.LedgerEntryResponse;
import com.paystream.ledger.api.dto.response.TransactionResponse;
import com.paystream.ledger.application.port.in.LedgerUseCase;
import com.paystream.ledger.application.port.out.LedgerEntryRepository;
import com.paystream.ledger.domain.model.EntryType;
import com.paystream.ledger.domain.model.LedgerEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Implements the double-entry bookkeeping invariant.
 *
 * Core rule: for every referenceId, the sum of all ledger entry amounts MUST equal zero.
 * This is validated BEFORE any persistence call. If validation fails, nothing is written.
 */
@Service
public class LedgerApplicationService implements LedgerUseCase {

    private static final Logger log = LoggerFactory.getLogger(LedgerApplicationService.class);

    private final LedgerEntryRepository ledgerEntryRepository;

    public LedgerApplicationService(LedgerEntryRepository ledgerEntryRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    // -------------------------------------------------------------------------
    // Double-entry creation — the central invariant enforcement point
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void createDoubleEntry(DoubleEntryRequest request) {
        long debitAmount  = request.debitEntry().amount();
        long creditAmount = request.creditEntry().amount();

        // Enforce double-entry invariant BEFORE any persistence
        if (debitAmount + creditAmount != 0) {
            throw new DomainException(ErrorCode.INVALID_STATE_TRANSITION,
                    String.format("Double-entry invariant violated for referenceId=%s: debit=%d + credit=%d = %d (must be 0)",
                            request.referenceId(), debitAmount, creditAmount, debitAmount + creditAmount));
        }

        String referenceId   = request.referenceId();
        String referenceType = request.referenceType();
        Instant now          = Instant.now();

        // Idempotency: if both entries already exist, this is a replay — skip silently
        boolean debitExists = ledgerEntryRepository.existsByReferenceIdAndAccountIdAndEntryType(
                referenceId, request.debitEntry().accountId(), "DEBIT");
        boolean creditExists = ledgerEntryRepository.existsByReferenceIdAndAccountIdAndEntryType(
                referenceId, request.creditEntry().accountId(), "CREDIT");

        if (debitExists && creditExists) {
            log.info("Idempotent double-entry — already recorded referenceId={} correlationId={}",
                    referenceId, MDC.get("correlationId"));
            return;
        }

        LedgerEntry debit = new LedgerEntry(
                IdGenerator.generate(),
                request.debitEntry().accountId(),
                request.debitEntry().entryType(),
                debitAmount,
                request.debitEntry().currency(),
                referenceId, referenceType,
                request.debitEntry().description(),
                now
        );

        LedgerEntry credit = new LedgerEntry(
                IdGenerator.generate(),
                request.creditEntry().accountId(),
                request.creditEntry().entryType(),
                creditAmount,
                request.creditEntry().currency(),
                referenceId, referenceType,
                request.creditEntry().description(),
                now
        );

        // Both entries are persisted atomically within this @Transactional boundary
        ledgerEntryRepository.save(debit);
        ledgerEntryRepository.save(credit);

        log.info("Double-entry recorded referenceId={} debitAccount={} creditAccount={} amount={} correlationId={}",
                referenceId, debit.getAccountId(), credit.getAccountId(),
                Math.abs(debitAmount), MDC.get("correlationId"));
    }

    // -------------------------------------------------------------------------
    // Balance query
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public BalanceResponse getBalance(String accountId) {
        long balance = ledgerEntryRepository.computeBalance(accountId);
        log.debug("Balance computed accountId={} balance={} correlationId={}",
                accountId, balance, MDC.get("correlationId"));
        return new BalanceResponse(accountId, balance, "INR"); // currency resolved from entries in full impl
    }

    // -------------------------------------------------------------------------
    // Entry listing
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public Page<LedgerEntryResponse> getEntries(String accountId, Pageable pageable) {
        return ledgerEntryRepository.findByAccountId(accountId, pageable)
                .map(this::toResponse);
    }

    // -------------------------------------------------------------------------
    // Transaction lookup (both legs + integrity check)
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public TransactionResponse getTransaction(String referenceId) {
        List<LedgerEntry> entries = ledgerEntryRepository.findByReferenceId(referenceId);
        if (entries.isEmpty()) {
            throw new ResourceNotFoundException("Transaction", referenceId);
        }

        long sum = entries.stream().mapToLong(LedgerEntry::getAmount).sum();
        boolean integrityValid = (sum == 0);

        if (!integrityValid) {
            log.error("LEDGER INTEGRITY FAILURE referenceId={} sum={} correlationId={}",
                    referenceId, sum, MDC.get("correlationId"));
        }

        List<LedgerEntryResponse> responses = entries.stream().map(this::toResponse).toList();
        String referenceType = entries.get(0).getReferenceType();

        return new TransactionResponse(referenceId, referenceType, responses, integrityValid);
    }

    // -------------------------------------------------------------------------
    // Kafka-driven single entry creation
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public void recordDebitFromWalletEvent(WalletDebitedEvent event) {
        // referenceId is the payment/refund ID that caused the debit
        String referenceId = event.referenceId() != null ? event.referenceId() : event.walletId();
        LedgerEntry entry = new LedgerEntry(
                IdGenerator.generate(),
                event.walletId(),
                EntryType.DEBIT,
                -Math.abs(event.amount()),  // negative for DEBIT
                event.currency(),
                referenceId,
                event.referenceType() != null ? event.referenceType() : "WALLET_DEBIT",
                "Wallet debit " + event.walletId() + " amount=" + event.amount(),
                Instant.now()
        );
        ledgerEntryRepository.save(entry);
        log.info("Ledger debit recorded walletId={} amount={} referenceId={} correlationId={}",
                event.walletId(), event.amount(), referenceId, MDC.get("correlationId"));
    }

    @Override
    @Transactional
    public void recordCreditFromWalletEvent(WalletCreditedEvent event) {
        String referenceId = event.referenceId() != null ? event.referenceId() : event.walletId();
        LedgerEntry entry = new LedgerEntry(
                IdGenerator.generate(),
                event.walletId(),
                EntryType.CREDIT,
                Math.abs(event.amount()),   // positive for CREDIT
                event.currency(),
                referenceId,
                event.referenceType() != null ? event.referenceType() : "WALLET_CREDIT",
                "Wallet credit " + event.walletId() + " amount=" + event.amount(),
                Instant.now()
        );
        ledgerEntryRepository.save(entry);
        log.info("Ledger credit recorded walletId={} amount={} referenceId={} correlationId={}",
                event.walletId(), event.amount(), referenceId, MDC.get("correlationId"));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private LedgerEntryResponse toResponse(LedgerEntry e) {
        return new LedgerEntryResponse(
                e.getId(), e.getAccountId(), e.getEntryType(), e.getAmount(),
                e.getCurrency(), e.getReferenceId(), e.getReferenceType(),
                e.getDescription(), e.getCreatedAt()
        );
    }
}
