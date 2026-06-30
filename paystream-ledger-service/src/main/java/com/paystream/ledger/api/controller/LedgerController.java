package com.paystream.ledger.api.controller;

import com.paystream.common.api.ApiResponse;
import com.paystream.ledger.api.dto.request.DoubleEntryRequest;
import com.paystream.ledger.api.dto.response.BalanceResponse;
import com.paystream.ledger.api.dto.response.LedgerEntryResponse;
import com.paystream.ledger.api.dto.response.TransactionResponse;
import com.paystream.ledger.application.port.in.LedgerUseCase;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for ledger operations.
 * POST /entries is an internal endpoint guarded by InternalServiceAuthFilter.
 * All methods delegate — zero business logic.
 */
@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {

    private final LedgerUseCase ledgerUseCase;

    public LedgerController(LedgerUseCase ledgerUseCase) {
        this.ledgerUseCase = ledgerUseCase;
    }

    /** Internal endpoint — requires X-Internal-Service-Key header. */
    @PostMapping("/entries")
    public ResponseEntity<ApiResponse<Void>> createDoubleEntry(
            @Valid @RequestBody DoubleEntryRequest request) {
        ledgerUseCase.createDoubleEntry(request);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/accounts/{accountId}/balance")
    public ResponseEntity<ApiResponse<BalanceResponse>> getBalance(
            @PathVariable String accountId) {
        return ResponseEntity.ok(ApiResponse.success(ledgerUseCase.getBalance(accountId)));
    }

    @GetMapping("/accounts/{accountId}/entries")
    public ResponseEntity<ApiResponse<Page<LedgerEntryResponse>>> getEntries(
            @PathVariable String accountId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(ledgerUseCase.getEntries(accountId, pageable)));
    }

    @GetMapping("/transactions/{referenceId}")
    public ResponseEntity<ApiResponse<TransactionResponse>> getTransaction(
            @PathVariable String referenceId) {
        return ResponseEntity.ok(ApiResponse.success(ledgerUseCase.getTransaction(referenceId)));
    }
}
