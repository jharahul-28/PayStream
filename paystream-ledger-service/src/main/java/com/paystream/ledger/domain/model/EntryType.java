package com.paystream.ledger.domain.model;

/** Direction of a ledger entry. DEBIT reduces the account balance; CREDIT increases it. */
public enum EntryType {
    DEBIT,
    CREDIT
}
