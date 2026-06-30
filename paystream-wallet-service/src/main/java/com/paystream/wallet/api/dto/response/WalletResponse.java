package com.paystream.wallet.api.dto.response;

import com.paystream.wallet.domain.model.WalletStatus;

import java.time.Instant;

/** Immutable response record returned for all wallet operations. */
public record WalletResponse(
        String       id,
        String       userId,
        long         balance,
        String       currency,
        WalletStatus status,
        int          version,
        Instant      createdAt,
        Instant      updatedAt
) {}
