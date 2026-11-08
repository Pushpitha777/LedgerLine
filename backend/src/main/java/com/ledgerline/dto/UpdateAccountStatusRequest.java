package com.ledgerline.dto;

import com.ledgerline.entity.AccountStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateAccountStatusRequest(
    @NotNull(message = "New account status is required")
    AccountStatus status,

    @Size(max = 255, message = "Reason cannot exceed 255 characters")
    String reason
) {}
