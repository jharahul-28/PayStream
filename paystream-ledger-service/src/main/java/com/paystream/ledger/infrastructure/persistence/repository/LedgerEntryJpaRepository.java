package com.paystream.ledger.infrastructure.persistence.repository;

import com.paystream.ledger.infrastructure.persistence.entity.LedgerEntryEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Spring Data JPA interface for the append-only ledger. */
public interface LedgerEntryJpaRepository extends JpaRepository<LedgerEntryEntity, String> {

    List<LedgerEntryEntity> findByReferenceId(String referenceId);

    Page<LedgerEntryEntity> findByAccountIdOrderByCreatedAtDesc(String accountId, Pageable pageable);

    boolean existsByReferenceIdAndAccountIdAndEntryType(String referenceId, String accountId,
                                                        @Param("entryType") String entryType);

    /**
     * Computes the current balance for an account using snapshot + incremental approach.
     * If a snapshot exists, sums only entries created after the snapshot to avoid full-table scans.
     * Uses native SQL to avoid loading entries into memory.
     */
    @Query(value = """
            SELECT COALESCE(
                (SELECT s.balance + COALESCE(
                    (SELECT SUM(le.amount)
                     FROM ledger_entries le
                     WHERE le.account_id = :accountId
                       AND le.created_at > s.snapshot_at), 0)
                 FROM account_balance_snapshots s
                 WHERE s.account_id = :accountId),
                (SELECT COALESCE(SUM(le.amount), 0)
                 FROM ledger_entries le
                 WHERE le.account_id = :accountId)
            )
            """, nativeQuery = true)
    Long computeBalance(@Param("accountId") String accountId);
}
