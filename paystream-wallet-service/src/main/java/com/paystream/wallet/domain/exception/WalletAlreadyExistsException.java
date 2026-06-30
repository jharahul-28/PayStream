package com.paystream.wallet.domain.exception;

import com.paystream.common.exception.DuplicateResourceException;

/** Thrown when a user attempts to create a second wallet for the same currency. */
public class WalletAlreadyExistsException extends DuplicateResourceException {

    public WalletAlreadyExistsException(String userId, String currency) {
        super("Wallet already exists for userId=" + userId + " currency=" + currency);
    }
}
