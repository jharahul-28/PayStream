package com.paystream.ledger.api.dto.response;

import com.paystream.ledger.domain.model.EntryType;

import java.time.Instant;

/** Immutable response record for a single ledger entry. */
public record LedgerEntryResponse(
        String    id,
        String    accountId,
        EntryType entryType,
        long      amount,
        String    currency,
        String    referenceId,
        String    referenceType,
        String    description,
        Instant   createdAt
) {}
