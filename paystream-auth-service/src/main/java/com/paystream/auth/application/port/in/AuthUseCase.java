package com.paystream.auth.application.port.in;

import com.paystream.auth.api.dto.response.AuthResponse;
import com.paystream.auth.api.dto.response.UserResponse;
import com.paystream.auth.application.command.LoginCommand;
import com.paystream.auth.application.command.RegisterCommand;

/** Input port — defines the use-case contract for authentication operations. */
public interface AuthUseCase {

    UserResponse register(RegisterCommand command);

    AuthResponse login(LoginCommand command);

    AuthResponse refresh(String rawRefreshToken);

    void logout(String accessToken);

    UserResponse getMe(String userId);
}
