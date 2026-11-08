package com.ledgerline.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record ErrorResponse(
    boolean success,
    int status,
    String error,
    String message,
    String path,
    Map<String, String> validationErrors,
    OffsetDateTime timestamp
) {
    public ErrorResponse(int status, String error, String message, String path) {
        this(false, status, error, message, path, null, OffsetDateTime.now());
    }

    public ErrorResponse(int status, String error, String message, String path, Map<String, String> validationErrors) {
        this(false, status, error, message, path, validationErrors, OffsetDateTime.now());
    }
}
