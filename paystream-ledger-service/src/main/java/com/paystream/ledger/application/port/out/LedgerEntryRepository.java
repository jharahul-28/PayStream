package com.paystream.ledger.application.port.out;

import com.paystream.ledger.domain.model.LedgerEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Output port — persistence contract for ledger entries. */
public interface LedgerEntryRepository {

    /** Saves a single ledger entry. Must be called within a transaction. */
    LedgerEntry save(LedgerEntry entry);

    List<LedgerEntry> findByReferenceId(String referenceId);

    Page<LedgerEntry> findByAccountId(String accountId, Pageable pageable);

    /** Computes balance from snapshot (if present) + incremental sum. Uses native SQL aggregate. */
    long computeBalance(String accountId);

    boolean existsByReferenceIdAndAccountIdAndEntryType(String referenceId, String accountId, String entryType);
}
