package com.paystream.auth.application.command;

public record LoginCommand(String email, String rawPassword) {}
