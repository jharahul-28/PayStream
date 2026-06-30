package com.paystream.wallet.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request to create a new wallet.
 * Currency must be a valid ISO 4217 code (3 uppercase letters).
 */
public record CreateWalletRequest(
        @NotBlank(message = "Currency is required")
        @Pattern(regexp = "[A-Z]{3}", message = "Currency must be a 3-letter ISO 4217 code")
        String currency
) {
    public CreateWalletRequest {
        currency = (currency != null) ? currency.toUpperCase() : null;
    }
}
