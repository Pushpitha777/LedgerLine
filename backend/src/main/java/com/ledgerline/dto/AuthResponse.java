package com.ledgerline.dto;

import java.util.Set;
import java.util.UUID;

public record AuthResponse(
    String token,
    String tokenType,
    UUID userId,
    String username,
    String email,
    String fullName,
    Set<String> roles
) {
    public static AuthResponse of(String token, UUID userId, String username, String email, String fullName, Set<String> roles) {
        return new AuthResponse(token, "Bearer", userId, username, email, fullName, roles);
    }
}
