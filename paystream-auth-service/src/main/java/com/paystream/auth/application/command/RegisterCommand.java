package com.paystream.auth.application.command;

import com.paystream.auth.domain.model.Role;

/**
 * Command object carrying validated registration input into the application service.
 * Immutable — constructed once at the controller boundary; application layer reads only.
 */
public record RegisterCommand(
        String email,
        String rawPassword,
        String fullName,
        Role role
) {}
