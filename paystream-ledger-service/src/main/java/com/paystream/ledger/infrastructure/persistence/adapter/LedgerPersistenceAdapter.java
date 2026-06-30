package com.paystream.ledger.infrastructure.persistence.adapter;

import com.paystream.ledger.application.port.out.LedgerEntryRepository;
import com.paystream.ledger.domain.model.EntryType;
import com.paystream.ledger.domain.model.LedgerEntry;
import com.paystream.ledger.infrastructure.persistence.entity.LedgerEntryEntity;
import com.paystream.ledger.infrastructure.persistence.repository.LedgerEntryJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Translates between domain {@link LedgerEntry} and JPA {@link LedgerEntryEntity}. */
@Component
public class LedgerPersistenceAdapter implements LedgerEntryRepository {

    private final LedgerEntryJpaRepository jpaRepository;

    public LedgerPersistenceAdapter(LedgerEntryJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    @Transactional
    public LedgerEntry save(LedgerEntry entry) {
        LedgerEntryEntity entity = new LedgerEntryEntity(
                entry.getId(), entry.getAccountId(), entry.getEntryType(), entry.getAmount(),
                entry.getCurrency(), entry.getReferenceId(), entry.getReferenceType(),
                entry.getDescription(), entry.getCreatedAt()
        );
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LedgerEntry> findByReferenceId(String referenceId) {
        return jpaRepository.findByReferenceId(referenceId).stream()
                .map(this::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<LedgerEntry> findByAccountId(String accountId, Pageable pageable) {
        return jpaRepository.findByAccountIdOrderByCreatedAtDesc(accountId, pageable)
                .map(this::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public long computeBalance(String accountId) {
        Long result = jpaRepository.computeBalance(accountId);
        return result != null ? result : 0L;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByReferenceIdAndAccountIdAndEntryType(String referenceId, String accountId, String entryType) {
        return jpaRepository.existsByReferenceIdAndAccountIdAndEntryType(referenceId, accountId, entryType);
    }

    private LedgerEntry toDomain(LedgerEntryEntity e) {
        return new LedgerEntry(
                e.getId(), e.getAccountId(), e.getEntryType(), e.getAmount(),
                e.getCurrency(), e.getReferenceId(), e.getReferenceType(),
                e.getDescription(), e.getCreatedAt()
        );
    }
}
