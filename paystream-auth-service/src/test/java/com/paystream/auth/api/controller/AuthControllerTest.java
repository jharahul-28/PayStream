package com.paystream.auth.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paystream.auth.api.dto.request.LoginRequest;
import com.paystream.auth.api.dto.request.RefreshRequest;
import com.paystream.auth.api.dto.request.RegisterRequest;
import com.paystream.auth.api.dto.response.AuthResponse;
import com.paystream.auth.api.dto.response.UserResponse;
import com.paystream.auth.domain.model.Role;
import com.paystream.auth.domain.model.UserStatus;
import com.paystream.auth.application.port.in.AuthUseCase;
import com.paystream.auth.security.jwt.JwtAuthFilter;
import com.paystream.auth.security.jwt.JwtService;
import com.paystream.common.api.ApiResponse;
import com.paystream.common.constant.ErrorCode;
import com.paystream.common.exception.AuthException;
import com.paystream.common.exception.DuplicateResourceException;
import com.paystream.common.exception.GlobalExceptionHandler;
import com.paystream.common.util.IdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.security.KeyPair;
import java.security.KeyPairGenerator;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AuthController.class)
@Import(GlobalExceptionHandler.class)
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    AuthUseCase authUseCase;

    @MockBean
    JwtService jwtService;

    @MockBean
    StringRedisTemplate redisTemplate;

    @MockBean
    ValueOperations<String, String> valueOperations;

    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final String LOGIN_URL    = "/api/v1/auth/login";
    private static final String REFRESH_URL  = "/api/v1/auth/refresh";
    private static final String ME_URL       = "/api/v1/auth/me";

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair keyPair = generator.generateKeyPair();

        // Allow unauthenticated access to public endpoints in this test context
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.hasKey(any())).thenReturn(false);
    }

    // -------------------------------------------------------------------------
    // Register
    // -------------------------------------------------------------------------

    @Test
    void register_validData_returns201() throws Exception {
        UserResponse response = new UserResponse(IdGenerator.generate(),
                "user@test.com", "Test User", Role.CUSTOMER, UserStatus.ACTIVE);
        when(authUseCase.register(any())).thenReturn(response);

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                "user@test.com", "Password1@", "Test User", Role.CUSTOMER))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.email").value("user@test.com"));
    }

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        when(authUseCase.register(any()))
                .thenThrow(new DuplicateResourceException("Email already registered"));

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                "dup@test.com", "Password1@", "Test User", Role.CUSTOMER))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.DUPLICATE_RESOURCE.getCode()));
    }

    @Test
    void register_weakPassword_returns400() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                "user@test.com", "weak", "Test User", Role.CUSTOMER))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_ERROR.getCode()));
    }

    @Test
    void register_invalidEmail_returns400() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest(
                                "not-an-email", "Password1@", "Test User", Role.CUSTOMER))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_ERROR.getCode()));
    }

    // -------------------------------------------------------------------------
    // Login
    // -------------------------------------------------------------------------

    @Test
    void login_correctCredentials_returns200WithTokens() throws Exception {
        AuthResponse auth = new AuthResponse("access-token", "refresh-token",
                900L, "Bearer", IdGenerator.generate(), "CUSTOMER");
        when(authUseCase.login(any())).thenReturn(auth);

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("user@test.com", "Password1@"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("access-token"))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(900));
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        when(authUseCase.login(any())).thenThrow(AuthException.invalid("Invalid credentials"));

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("user@test.com", "WrongPass1@"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.AUTH_INVALID.getCode()));
    }

    @Test
    void login_accountLocked_returns423() throws Exception {
        when(authUseCase.login(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.LOCKED, "Account is temporarily locked"));

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new LoginRequest("locked@test.com", "Password1@"))))
                .andExpect(status().isLocked());
    }

    // -------------------------------------------------------------------------
    // Refresh
    // -------------------------------------------------------------------------

    @Test
    void refresh_validToken_returns200WithNewTokens() throws Exception {
        AuthResponse auth = new AuthResponse("new-access", "new-refresh",
                900L, "Bearer", IdGenerator.generate(), "CUSTOMER");
        when(authUseCase.refresh(any())).thenReturn(auth);

        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("valid-refresh-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("new-access"));
    }

    @Test
    void refresh_expiredToken_returns401() throws Exception {
        when(authUseCase.refresh(any())).thenThrow(AuthException.expired());

        mockMvc.perform(post(REFRESH_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshRequest("expired-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.AUTH_EXPIRED.getCode()));
    }

    // -------------------------------------------------------------------------
    // Get /me — requires authentication (tested via MockMvc with injected security context)
    // -------------------------------------------------------------------------

    @Test
    void getMe_withoutToken_returns401() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized());
    }
}
