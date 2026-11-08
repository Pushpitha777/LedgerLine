package com.ledgerline.dto;

import com.ledgerline.entity.Role;
import com.ledgerline.entity.User;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record AdminUserResponse(
    UUID id,
    String username,
    String email,
    String fullName,
    String status,
    Set<String> roles,
    OffsetDateTime createdAt
) {
    public static AdminUserResponse of(User user) {
        Set<String> roleNames = user.getRoles().stream()
                .map(Role::getName)
                .map(Enum::name)
                .collect(Collectors.toSet());

        return new AdminUserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getStatus(),
                roleNames,
                user.getCreatedAt()
        );
    }
}
