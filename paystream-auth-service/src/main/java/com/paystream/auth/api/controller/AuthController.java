package com.paystream.auth.api.controller;

import com.paystream.auth.api.dto.request.LoginRequest;
import com.paystream.auth.api.dto.request.RefreshRequest;
import com.paystream.auth.api.dto.request.RegisterRequest;
import com.paystream.auth.api.dto.response.AuthResponse;
import com.paystream.auth.api.dto.response.UserResponse;
import com.paystream.auth.application.command.LoginCommand;
import com.paystream.auth.application.command.RegisterCommand;
import com.paystream.auth.application.port.in.AuthUseCase;
import com.paystream.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for authentication operations.
 * Each method is one line of delegation — zero business logic here.
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthUseCase authUseCase;

    public AuthController(AuthUseCase authUseCase) {
        this.authUseCase = authUseCase;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<UserResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(authUseCase.register(
                        new RegisterCommand(request.email(), request.password(), request.fullName(), request.role()))));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                authUseCase.login(new LoginCommand(request.email(), request.password()))));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authUseCase.refresh(request.refreshToken())));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        authUseCase.logout(token);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> getMe(
            @AuthenticationPrincipal String userId) {
        return ResponseEntity.ok(ApiResponse.success(authUseCase.getMe(userId)));
    }
}
