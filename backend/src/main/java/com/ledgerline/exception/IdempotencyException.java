package com.ledgerline.exception;

public class IdempotencyException extends ApiException {
    public IdempotencyException(String message) {
        super(message);
    }
}
