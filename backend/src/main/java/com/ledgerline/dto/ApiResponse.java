package com.ledgerline.dto;

import java.time.OffsetDateTime;

public record ApiResponse<T>(
    boolean success,
    String message,
    T data,
    OffsetDateTime timestamp
) {
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "Operation successful", data, OffsetDateTime.now());
    }

    public static <T> ApiResponse<T> created(String message, T data) {
        return new ApiResponse<>(true, message, data, OffsetDateTime.now());
    }
}
