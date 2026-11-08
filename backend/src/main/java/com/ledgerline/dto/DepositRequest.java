package com.ledgerline.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

public record DepositRequest(
    @NotNull(message = "Destination account ID is required")
    UUID destinationAccountId,

    @NotNull(message = "Deposit amount is required")
    @DecimalMin(value = "0.01", message = "Deposit amount must be at least ₹0.01")
    BigDecimal amountRupees,

    @Size(max = 255, message = "Description cannot exceed 255 characters")
    String description
) {}
