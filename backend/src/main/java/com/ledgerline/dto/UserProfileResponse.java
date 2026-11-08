package com.ledgerline.dto;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record UserProfileResponse(
    UUID id,
    String username,
    String email,
    String fullName,
    String status,
    Set<String> roles,
    OffsetDateTime createdAt
) {}
