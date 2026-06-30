package com.paystream.ledger.application.port.in;

import com.paystream.common.event.wallet.WalletCreditedEvent;
import com.paystream.common.event.wallet.WalletDebitedEvent;
import com.paystream.ledger.api.dto.request.DoubleEntryRequest;
import com.paystream.ledger.api.dto.response.BalanceResponse;
import com.paystream.ledger.api.dto.response.LedgerEntryResponse;
import com.paystream.ledger.api.dto.response.TransactionResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Input port — all ledger use-cases. */
public interface LedgerUseCase {

    /** Creates a balanced double entry (debit + credit). Validates sum == 0 before persisting. */
    void createDoubleEntry(DoubleEntryRequest request);

    /** Creates a single ledger entry from a wallet debited Kafka event. */
    void recordDebitFromWalletEvent(WalletDebitedEvent event);

    /** Creates a single ledger entry from a wallet credited Kafka event. */
    void recordCreditFromWalletEvent(WalletCreditedEvent event);

    BalanceResponse getBalance(String accountId);

    Page<LedgerEntryResponse> getEntries(String accountId, Pageable pageable);

    TransactionResponse getTransaction(String referenceId);
}
