package com.ledgerline.dto;

public record CachedResponse(
    int statusCode,
    String responseBody
) {}
