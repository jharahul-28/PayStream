package com.paystream.wallet.api.controller;

import com.paystream.common.api.ApiResponse;
import com.paystream.wallet.api.dto.request.CreateWalletRequest;
import com.paystream.wallet.api.dto.request.DebitCreditRequest;
import com.paystream.wallet.api.dto.response.WalletResponse;
import com.paystream.wallet.application.port.in.WalletUseCase;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for wallet operations.
 * Each method is a one-line delegation — zero business logic.
 * User identity comes from the X-User-Id header injected by the API Gateway.
 */
@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final WalletUseCase walletUseCase;

    public WalletController(WalletUseCase walletUseCase) {
        this.walletUseCase = walletUseCase;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<WalletResponse>> createWallet(
            @RequestHeader("X-User-Id") String userId,
            @Valid @RequestBody CreateWalletRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(walletUseCase.createWallet(userId, request)));
    }

    @GetMapping("/my")
    public ResponseEntity<ApiResponse<WalletResponse>> getMyWallet(
            @RequestHeader("X-User-Id") String userId,
            @RequestParam(defaultValue = "INR") String currency) {
        return ResponseEntity.ok(ApiResponse.success(walletUseCase.getMyWallet(userId, currency)));
    }

    @GetMapping("/{walletId}")
    public ResponseEntity<ApiResponse<WalletResponse>> getWallet(
            @PathVariable String walletId) {
        return ResponseEntity.ok(ApiResponse.success(walletUseCase.getWalletById(walletId)));
    }

    /** Internal endpoint — requires X-Internal-Service-Key header (validated by InternalServiceAuthFilter). */
    @PostMapping("/{walletId}/debit")
    public ResponseEntity<ApiResponse<WalletResponse>> debit(
            @PathVariable String walletId,
            @Valid @RequestBody DebitCreditRequest request) {
        return ResponseEntity.ok(ApiResponse.success(walletUseCase.debit(walletId, request)));
    }

    /** Internal endpoint — requires X-Internal-Service-Key header (validated by InternalServiceAuthFilter). */
    @PostMapping("/{walletId}/credit")
    public ResponseEntity<ApiResponse<WalletResponse>> credit(
            @PathVariable String walletId,
            @Valid @RequestBody DebitCreditRequest request) {
        return ResponseEntity.ok(ApiResponse.success(walletUseCase.credit(walletId, request)));
    }

    @PostMapping("/{walletId}/freeze")
    public ResponseEntity<ApiResponse<WalletResponse>> freeze(@PathVariable String walletId) {
        return ResponseEntity.ok(ApiResponse.success(walletUseCase.freezeWallet(walletId)));
    }

    @PostMapping("/{walletId}/unfreeze")
    public ResponseEntity<ApiResponse<WalletResponse>> unfreeze(@PathVariable String walletId) {
        return ResponseEntity.ok(ApiResponse.success(walletUseCase.unfreezeWallet(walletId)));
    }
}
