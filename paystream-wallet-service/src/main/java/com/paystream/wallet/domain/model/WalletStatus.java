package com.paystream.wallet.domain.model;

/** Lifecycle states of a wallet. Transitions: ACTIVE <-> FROZEN, ACTIVE -> CLOSED. */
public enum WalletStatus {
    ACTIVE,
    FROZEN,
    CLOSED
}
