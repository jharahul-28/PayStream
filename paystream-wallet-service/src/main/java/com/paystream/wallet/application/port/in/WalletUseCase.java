package com.paystream.wallet.application.port.in;

import com.paystream.wallet.api.dto.request.CreateWalletRequest;
import com.paystream.wallet.api.dto.request.DebitCreditRequest;
import com.paystream.wallet.api.dto.response.WalletResponse;

/** Input port — defines all wallet use-cases the application supports. */
public interface WalletUseCase {

    WalletResponse createWallet(String userId, CreateWalletRequest request);

    WalletResponse getMyWallet(String userId, String currency);

    WalletResponse getWalletById(String walletId);

    WalletResponse debit(String walletId, DebitCreditRequest request);

    WalletResponse credit(String walletId, DebitCreditRequest request);

    WalletResponse freezeWallet(String walletId);

    WalletResponse unfreezeWallet(String walletId);
}
