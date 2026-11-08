package com.ledgerline.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateAccountRequest(
    @NotBlank(message = "Account type is required")
    @Pattern(regexp = "^(SAVINGS|CHECKING)$", message = "Account type must be either SAVINGS or CHECKING")
    String accountType
) {}
